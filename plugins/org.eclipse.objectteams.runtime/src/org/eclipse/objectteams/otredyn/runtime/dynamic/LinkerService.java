package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.List;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.objectteams.ITeam;

public class LinkerService {

	public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest) {

		final MethodHandle target;

		CallSiteDescriptor descriptor = linkRequest.getCallSiteDescriptor();
		Lookup lookup = new Lookup(descriptor.getLookup());
		MethodType baseMethodType = descriptor.getType();
		String joinpointDescription = descriptor.getJoinpointDescriptor();
		int joinpointId = TeamManager.getJoinpointId(joinpointDescription);
		ITeam[] teams = TeamManager.getTeams(joinpointId);

		if (teams.length > 0) {

			int[] callinIds = TeamManager.getCallinIds(joinpointId);
			// TODO maybe set current deapth of invocation into linkRequest
			// It could happen that there is a call next. Then we either need to look deeper
			// in the Team hierarchy, or need to look deeper in the active bindings for that
			// joinpoint
			List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(teams[0],
					joinpointDescription);
			// TODO maybe set current deapth of invocation into linkRequest
			IBinding binding = sortedCallinBindings.get(0);

			MethodHandle roleMethodHandle = lookup.findRoleMethod(binding, teams[0]);

			switch (binding.getCallinModifier()) {
			case REPLACE:
				// Insert the parameters as prebound values into the role method
				// As we already now them.
				MethodHandle reducedRoleMethodHandle = MethodHandles.insertArguments(roleMethodHandle, 2, teams, 0,
						callinIds);
				// _OT$liftTo$... A convenient function of OTJ that takes a base class as
				// argument and returns the interface type of the role. This will be directly
				// invoked on the corresponding team.
				MethodHandle convertBaseToRoleObjectHandle = lookup
						.findVirtual(teams[0].getClass(), "_OT$liftTo$" + binding.getRoleClassName(), MethodType
								.methodType(lookup.getRoleType(binding, teams[0].getClass(), true), lookup.lookupClass()))
						.bindTo(teams[0]);
				// The role interface type needs to be cast to the role implementation type
				// RoleName -> __OT__RoleName.
				MethodHandle adaptedConvertBaseToRoleObjectHandle = convertBaseToRoleObjectHandle.asType(
						MethodType.methodType(lookup.getRoleType(binding, teams[0].getClass(), false), lookup.lookupClass()));
				// Lift the base object to its role object and insert it as first parameter as
				// we will call the role method on the result.
				MethodHandle convertBaseToRoleObject = MethodHandles.filterArguments(reducedRoleMethodHandle, 0,
						adaptedConvertBaseToRoleObjectHandle);
				// A method type describing the base method with two preceding BaseClass
				// parameters.
				MethodType doubleBaseType = baseMethodType.insertParameterTypes(0, baseMethodType.parameterType(0));
				// TODO: Need to add the parameters of the role method
				// TODO: Maybe also need to add if there is a mapping
				// Cast the first two parameters from (BaseClass, IBoundBase2) -> (BaseClass,
				// BaseClass) as BaseClass implements IBoundBase2.
				MethodHandle doubledFirstParamOfBaseMethodHandle = convertBaseToRoleObject.asType(doubleBaseType);
				// The base object needs to be given twice, as the first one will be converted
				// into the corresponding role object that is played by that base object.
				MethodHandle baseMethodEquivalentHandle = MethodHandles
						.permuteArguments(doubledFirstParamOfBaseMethodHandle, baseMethodType, 0, 0, 1, 2);
				target = baseMethodEquivalentHandle;
				break;
			default:
				// BEFORE, AFTER
				throw new IllegalAccessError("LinkerService: " + linkRequest.toString());
			}
		} else {
			target = lookup.findOwnVirtual("_OT$callOrig", baseMethodType.dropParameterTypes(0, 1));
		}
		SwitchPoint switchPoint = new SwitchPoint();
		TeamManager.registerSwitchPoint(switchPoint, joinpointId);
		return new GuardedInvocation(target, switchPoint);
	}

}
