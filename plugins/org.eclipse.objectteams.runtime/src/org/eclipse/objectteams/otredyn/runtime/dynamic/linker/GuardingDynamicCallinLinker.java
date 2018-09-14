package org.eclipse.objectteams.otredyn.runtime.dynamic.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.IBinding.CallinModifier;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.eclipse.objectteams.otredyn.runtime.dynamic.DynamicCallSiteDescriptor;
import org.eclipse.objectteams.otredyn.runtime.dynamic.ObjectTeamsLookup;
import org.objectteams.ITeam;

import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;

public class GuardingDynamicCallinLinker implements GuardingDynamicLinker {

	private final static boolean DEBUGGING = true;

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
		final ITeam[] teams = TeamManager.getTeams(joinpointId);
		final int[] callinIds = TeamManager.getCallinIds(joinpointId);

		MethodHandle beforeComposition = null;
		MethodHandle afterComposition = null;
		MethodHandle replace = null;

		HashSet<IBinding> processedBindings = new HashSet<>();
		boolean processing = true;

		for (ITeam currentTeam : teams) {

			List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					desc.getJoinpointDescriptor());
			sortedCallinBindings.removeIf(processedBindings::contains);
			
			for (IBinding binding : sortedCallinBindings) {

				processedBindings.add(binding);

				MethodHandle roleMethod = ObjectTeamsLookup.findRoleMethod(desc.getLookup(), binding, currentTeam);
				//System.out.println("BEFORE: " + roleMethod);
				if(binding.getCallinModifier() == CallinModifier.REPLACE)
					roleMethod = MethodHandles.insertArguments(roleMethod, 1, null, null, null);
				//System.out.println("AFTER: " + roleMethod);
				MethodHandle liftRoleHandle = ObjectTeamsLookup.findLifting(desc.getLookup(), binding,
						currentTeam.getClass());
				MethodHandle liftRole = liftRoleHandle.bindTo(currentTeam);

				switch (binding.getCallinModifier()) {
				case BEFORE:
					if (!processing) {
						break;
					}
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
					if (replace != null && !processing) {
						break;
					}
					replace = resolveReplaceCallin(baseMethodType, teams, callinIds, roleMethod, liftRole);
					processing = false;
					break;
				}
			}
		}

		MethodHandle compositionHandle = (replace == null)
				? ObjectTeamsLookup.findOrig(desc.getLookup(), baseMethodType)
				: replace;

		if (beforeComposition != null) {
			compositionHandle = MethodHandles.foldArguments(compositionHandle, beforeComposition);
		}
		if (afterComposition != null) {
			MethodType oldType = compositionHandle.type();
			compositionHandle = compositionHandle.asType(oldType.changeReturnType(void.class));
			compositionHandle = MethodHandles.foldArguments(afterComposition, compositionHandle);
			compositionHandle = compositionHandle.asType(oldType);
		}

		// TODO: Share a switchpoint for a joinpointid ?
		final SwitchPoint sp = new SwitchPoint();
		TeamManager.registerSwitchPoint(sp, joinpointId);
		return new GuardedInvocation(compositionHandle, sp);
	}

	private MethodHandle resolveBeforeAfterCAllin(MethodHandle roleMethod, MethodHandle liftRole) {
		final MethodHandle adapted = MethodHandles.filterArguments(roleMethod, 0, liftRole);
		final MethodHandle reduced = MethodHandles.dropArguments(adapted, 1, int.class, Object[].class);
		return reduced;
	}

	private MethodHandle resolveReplaceCallin(MethodType baseMethodType, ITeam[] teams, int[] callinIds,
			MethodHandle roleMethod, MethodHandle liftRole) {
		// If there is a replace callin we will need to centrally store the callin
		// context (teams[], callinIds[]) for that callsite.
		final MethodHandle reducedRoleMethod = MethodHandles.insertArguments(roleMethod, 2, teams, 0, callinIds);
		MethodHandle adaptedReplace = MethodHandles.filterArguments(reducedRoleMethod, 0, liftRole);
		MethodType doubleBaseType = baseMethodType.insertParameterTypes(0, baseMethodType.parameterType(0));

		if (reducedRoleMethod.type().parameterCount() > 4) {
			final int spreadCount = reducedRoleMethod.type().parameterCount() - 4;
			final int dropEnd = adaptedReplace.type().parameterCount();
			final int dropBegin = dropEnd - spreadCount;

			adaptedReplace = adaptedReplace.asSpreader(Object[].class, spreadCount);
			adaptedReplace = MethodHandles.permuteArguments(adaptedReplace,
					adaptedReplace.type().dropParameterTypes(dropBegin, dropEnd), 0, 1, 2, 3, 3);
		}
		// TODO: Maybe also need to add if there is a mappng
		// Cast the first two parameters from (BaseClass, IBoundBase2) -> (BaseClass,
		// BaseClass) as BaseClass implements IBoundBase2.
		// TODO: Implement as TypeConverter see
		// https://docs.oracle.com/javase/9/docs/api/jdk/dynalink/linker/GuardingTypeConverterFactory.html
		MethodHandle doubledFirstParamOfBaseMethodHandle = adaptedReplace.asType(doubleBaseType);
		// The base object needs to be given twice, as the first one will be converted
		// into the corresponding role object that is played by that base object.
		return MethodHandles.permuteArguments(doubledFirstParamOfBaseMethodHandle, baseMethodType, 0, 0, 1, 2);
	}

}
