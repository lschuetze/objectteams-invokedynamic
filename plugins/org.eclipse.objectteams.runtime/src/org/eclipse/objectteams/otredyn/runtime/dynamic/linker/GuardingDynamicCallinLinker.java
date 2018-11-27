package org.eclipse.objectteams.otredyn.runtime.dynamic.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.HashSet;
import java.util.List;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.eclipse.objectteams.otredyn.runtime.dynamic.CallSiteContext;
import org.eclipse.objectteams.otredyn.runtime.dynamic.DynamicCallSiteDescriptor;
import org.eclipse.objectteams.otredyn.runtime.dynamic.ObjectTeamsLookup;
import org.objectteams.ITeam;

import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Guards;

public class GuardingDynamicCallinLinker implements GuardingDynamicLinker {

	private static final MethodHandle MH_GET_TEAMS = getTeams();

	private static final MethodHandle MH_GET_CALLINIDS = getCallinIds();

	@Override
	public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, LinkerServices linkerServices)
			throws Exception {
		if (!(linkRequest.getCallSiteDescriptor() instanceof DynamicCallSiteDescriptor)) {
			throw new LinkageError("CallSiteDescriptor is no DynamicCallSiteDescriptor");
		}

		final DynamicCallSiteDescriptor desc = (DynamicCallSiteDescriptor) linkRequest.getCallSiteDescriptor();
		GuardedInvocation result = null;

		switch (DynamicCallSiteDescriptor.getStandardOperation(desc)) {

		case CALL:

			switch (desc.getFlags()) {
			case DynamicCallSiteDescriptor.CALL_IN:
				result = constructCallinComposition(desc, linkRequest, linkerServices);
				break;
			case DynamicCallSiteDescriptor.CALL_NEXT:
				result = constructCallinComposition(desc, linkRequest, linkerServices);
				break;
			}

			break;
		default:
			// TODO other cases
			throw new AssertionError(DynamicCallSiteDescriptor.getOperand(desc));
		}
		return result;
	}

	/**
	 * Constructs a composition of active callins. Before and after callins can be
	 * called subsequently without guards. In case there is a replace callin it will
	 * be called instead of the original function.
	 * 
	 * @param desc
	 * @param linkRequest
	 * @param linkerServices
	 * @return
	 */
	private GuardedInvocation constructCallinComposition(DynamicCallSiteDescriptor desc, LinkRequest linkRequest,
			LinkerServices linkerServices) {
		
		final int joinpointId = TeamManager.getJoinpointId(desc.getJoinpointDescriptor());
		final MethodType baseMethodType = desc.getMethodType();
		// TODO: Replace with MHs
		CallSiteContext context = CallSiteContext.contexts.get(desc.getJoinpointDescriptor());
		final int[] callinIds = TeamManager.getCallinIds(joinpointId);

		MethodHandle beforeComposition = null;
		MethodHandle afterComposition = null;
		MethodHandle replace = null;

		HashSet<IBinding> processedBindings = context.proccessedBindings; // new HashSet<>();
		boolean stopSearch = false;

		for (int i = context.index; i < context.teams.length; i++) {
			context.index++;

			ITeam currentTeam = context.teams[i];

			List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					desc.getJoinpointDescriptor());


			sortedCallinBindings.removeIf(processedBindings::contains);

			for (IBinding binding : sortedCallinBindings) {
				processedBindings.add(binding);

				MethodHandle roleMethod = ObjectTeamsLookup.findRoleMethod(desc.getLookup(), binding, currentTeam);
				// System.out.println("BEFORE: " + roleMethod);
//				if(binding.getCallinModifier() == CallinModifier.REPLACE)
//					roleMethod = MethodHandles.insertArguments(roleMethod, 1, desc.getLookup(), "moep", desc.getMethodType());
				// System.out.println("AFTER: " + roleMethod);
				MethodHandle liftRoleHandle = ObjectTeamsLookup.findLifting(desc.getLookup(), binding,
						currentTeam.getClass());
				MethodHandle liftRole = liftRoleHandle.bindTo(currentTeam);

				switch (binding.getCallinModifier()) {
				case BEFORE:
					MethodHandle beforeHandle = resolveBeforeAfterCAllin(roleMethod, liftRole);
					beforeComposition = (beforeComposition == null) ? beforeHandle
							: MethodHandles.foldArguments(beforeComposition, beforeHandle);
					break;

				case AFTER:
					MethodHandle afterHandle = resolveBeforeAfterCAllin(roleMethod, liftRole);
					afterComposition = (afterComposition == null) ? afterHandle
							: MethodHandles.foldArguments(afterComposition, afterHandle);
					break;

				case REPLACE:
					replace = resolveReplaceCallin(baseMethodType, context.teams, callinIds, roleMethod, liftRole);
					stopSearch = true;
					break;
				}
				// Check if we found a callin
				if (stopSearch) {
					break;
				}
			}
			// Check if we found a callin
			if (stopSearch) {
				break;
			}
		}

		MethodHandle compositionHandle = null;
		if (replace == null) {
			compositionHandle = ObjectTeamsLookup.findOrig(desc.getLookup(), context.baseClass, baseMethodType);
			compositionHandle = MethodHandles.insertArguments(compositionHandle, 1, context.bmId);
			compositionHandle = compositionHandle.asCollector(Object[].class, 1);
		} else {
			compositionHandle = replace;
		}

		if (beforeComposition != null) {
			compositionHandle = MethodHandles.foldArguments(compositionHandle, beforeComposition);
		}

		if (afterComposition != null) {
			// if compositionHandle returns a value it need to be ignored and stored for
			// later
//			compositionHandle = MethodHandles.foldArguments(afterComposition, compositionHandle);
		}

		// TODO: Share a switchpoint for a joinpointid ?
		SwitchPoint sp = TeamManager.getSwitchPoint(joinpointId);
		if(sp == null) {
			sp = new SwitchPoint();
			TeamManager.registerSwitchPoint(sp, joinpointId);
		}
		return new GuardedInvocation(compositionHandle, sp);
	}
	
	private MethodHandle resolveBeforeAfterCAllin(MethodHandle roleMethod, MethodHandle liftRole) {
		final MethodHandle adapted = MethodHandles.filterArguments(roleMethod, 0, liftRole);
//		final MethodHandle reduced = MethodHandles.dropArguments(adapted, 1, int.class, Object[].class);
		return adapted;
	}

	private MethodHandle resolveReplaceCallin(MethodType baseMethodType, ITeam[] teams, int[] callinIds,
			MethodHandle roleMethod, MethodHandle liftRole) {
		// If there is a replace callin we will need to centrally store the callin
		// context (teams[], callinIds[]) for that callsite.
//		final MethodHandle reducedRoleMethod = MethodHandles.insertArguments(roleMethod, 2, teams, 0, callinIds);
		MethodHandle adaptedReplace = MethodHandles.filterArguments(roleMethod, 0, liftRole);
		return adaptedReplace;
//		MethodType doubleBaseType = baseMethodType.insertParameterTypes(0, baseMethodType.parameterType(0));

//		if (roleMethod.type().parameterCount() > 4) {
//			final int spreadCount = roleMethod.type().parameterCount() - 4;
//			final int dropEnd = adaptedReplace.type().parameterCount();
//			final int dropBegin = dropEnd - spreadCount;
//
//			adaptedReplace = adaptedReplace.asSpreader(Object[].class, spreadCount);
//			adaptedReplace = MethodHandles.permuteArguments(adaptedReplace,
//					adaptedReplace.type().dropParameterTypes(dropBegin, dropEnd), 0, 1, 2, 3, 3);
//		}
		// TODO: Maybe also need to add if there is a mappng
		// Cast the first two parameters from (BaseClass, IBoundBase2) -> (BaseClass,
		// BaseClass) as BaseClass implements IBoundBase2.
		// TODO: Implement as TypeConverter see
		// https://docs.oracle.com/javase/9/docs/api/jdk/dynalink/linker/GuardingTypeConverterFactory.html
//		MethodHandle doubledFirstParamOfBaseMethodHandle = adaptedReplace.asType(doubleBaseType);
		// The base object needs to be given twice, as the first one will be converted
		// into the corresponding role object that is played by that base object.
//		final int[] reordering = new int[doubleBaseType.parameterCount()];
//		reordering[0] = 0;
//		reordering[1] = 0;
//		for(int i = 1; i < reordering.length; i++) {
//			reordering[i+1] = i;
//		}
//		return MethodHandles.permuteArguments(adaptedReplace, doubleBaseType, reordering);
	}

	private static MethodHandle getTeams() {
		return null;
	}

	private static MethodHandle getCallinIds() {
		return null;
	}

}
