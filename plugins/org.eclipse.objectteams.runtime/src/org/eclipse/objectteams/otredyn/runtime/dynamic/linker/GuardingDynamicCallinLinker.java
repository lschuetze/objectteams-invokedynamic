package org.eclipse.objectteams.otredyn.runtime.dynamic.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Collection;
import java.util.HashSet;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.eclipse.objectteams.otredyn.runtime.dynamic.DynamicCallSiteDescriptor;
import org.eclipse.objectteams.otredyn.runtime.dynamic.ObjectTeamsLookup;
import org.objectteams.ITeam;

import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;

public class GuardingDynamicCallinLinker implements GuardingDynamicLinker {

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
				result = constructCallNext();
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

		final Collection<IBinding> alreadyProcessesBindings = new HashSet<>();

		for (final ITeam currentTeam : teams) {
			final Class<?> currentTeamClass = currentTeam.getClass();

			final Collection<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					desc.getJoinpointDescriptor());
			
			for (final IBinding binding : sortedCallinBindings) {

				if (!alreadyProcessesBindings.contains(binding)) {
					alreadyProcessesBindings.add(binding);
				} else {
					continue;
				}

				final MethodHandle roleMethod = ObjectTeamsLookup.findRoleMethod(desc.getLookup(), binding,
						currentTeam);
				MethodHandle liftRole = ObjectTeamsLookup.findLifting(desc.getLookup(), binding, currentTeamClass)
						.bindTo(currentTeam);

				switch (binding.getCallinModifier()) {
				case BEFORE:
					final MethodHandle adaptedBefore = MethodHandles.filterArguments(roleMethod, 0, liftRole);
					final MethodHandle reducedBefore = MethodHandles.dropArguments(adaptedBefore, 1, int.class,
							Object[].class);

					beforeComposition = (beforeComposition == null) ? reducedBefore
							: MethodHandles.foldArguments(beforeComposition, reducedBefore);

					break;

				case AFTER:
					final MethodHandle adaptedAfter = MethodHandles.filterArguments(roleMethod, 0, liftRole);
					final MethodHandle reducedAfter = MethodHandles.dropArguments(adaptedAfter, 1, int.class,
							Object[].class);

					afterComposition = (afterComposition == null) ? reducedAfter
							: MethodHandles.foldArguments(afterComposition, reducedAfter);
					break;

				case REPLACE:
					if (replace != null) {
						break;
					}
					// If there is a replace callin we will need to centrally store the callin
					// context (teams[], callinIds[]) for that callsite.
					final MethodHandle reducedRoleMethod = MethodHandles.insertArguments(roleMethod, 2, teams, 0,
							callinIds);
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
					replace = MethodHandles.permuteArguments(doubledFirstParamOfBaseMethodHandle, baseMethodType, 0, 0,
							1, 2);
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
		//
		return new GuardedInvocation(compositionHandle, sp);
	}

	private GuardedInvocation constructCallNext() {
		return null;
	}

}
