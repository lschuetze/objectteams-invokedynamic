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
import jdk.dynalink.linker.support.Lookup;

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

		for (ITeam currentTeam : context.teams) {
			context.index++;

			List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					desc.getJoinpointDescriptor());

			sortedCallinBindings.removeIf(processedBindings::contains);

			for (IBinding binding : sortedCallinBindings) {
				processedBindings.add(binding);

				MethodHandle roleMethod = ObjectTeamsLookup.findRoleMethod(desc.getLookup(), binding, currentTeam);
				MethodHandle liftRoleHandle = ObjectTeamsLookup.findLifting(desc.getLookup(), binding,
						currentTeam.getClass());
				MethodHandle liftRole = liftRoleHandle.bindTo(currentTeam);

				MethodHandle callinHandle = MethodHandles.filterArguments(roleMethod, 0, liftRole);

				switch (binding.getCallinModifier()) {
				case BEFORE:
					beforeComposition = (beforeComposition == null) ? callinHandle
							: MethodHandles.foldArguments(beforeComposition, callinHandle);
					break;

				case AFTER:
					afterComposition = (afterComposition == null) ? callinHandle
							: MethodHandles.foldArguments(afterComposition, callinHandle);
					break;

				case REPLACE:
					replace = callinHandle;
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
		if (sp == null) {
			sp = new SwitchPoint();
			TeamManager.registerSwitchPoint(sp, joinpointId);
		}
		return new GuardedInvocation(compositionHandle, sp);
	}

	private static MethodHandle getTeams() {
		return null;
	}

	private static MethodHandle getCallinIds() {
		return null;
	}

}
