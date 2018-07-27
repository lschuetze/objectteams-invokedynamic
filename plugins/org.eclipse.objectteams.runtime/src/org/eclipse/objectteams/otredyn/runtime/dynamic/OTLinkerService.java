package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.objectteams.ITeam;

public class LinkerService {

	public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest) {

		final CallSiteDescriptor descriptor = linkRequest.getCallSiteDescriptor();
		final Lookup lookup = new Lookup(descriptor.getLookup());
		final MethodType baseMethodType = descriptor.getType();
		final String joinpointDescription = descriptor.getJoinpointDescriptor();
		final int joinpointId = TeamManager.getJoinpointId(joinpointDescription);
		final ITeam[] teams = TeamManager.getTeams(joinpointId);
		final int[] callinIds = TeamManager.getCallinIds(joinpointId);

		Optional<MethodHandle> callBefore = composeCallBefore(baseMethodType, teams, callinIds, joinpointDescription,
				lookup);
		MethodHandle target = callReplace(baseMethodType, teams, callinIds, joinpointDescription, lookup)
				.orElse(callOrig(lookup, baseMethodType));
		Optional<MethodHandle> callAfter = composeCallAfter(baseMethodType, teams, callinIds, joinpointDescription,
				lookup);

		if (callBefore.isPresent()) {
			// Compose target with before callins
			target = MethodHandles.foldArguments(target, callBefore.get());
		}

		if (callAfter.isPresent()) {
			// Compose target with after callins
			MethodHandle after = callAfter.get();
			after.asType(after.type().changeReturnType(Object.class));
			target = MethodHandles.filterReturnValue(target, after);
		}

		SwitchPoint switchPoint = new SwitchPoint();
		TeamManager.registerSwitchPoint(switchPoint, joinpointId);
		return new GuardedInvocation(target, switchPoint);
	}

	private Optional<MethodHandle> composeCallAfter(final MethodType baseMethodType, final ITeam[] teams,
			final int[] callinIds, final String joinpointDescription, final Lookup lookup) {

		MethodHandle resultHandle = null;

		for (final ITeam currentTeam : teams) {
			final Class<?> currentTeamClass = currentTeam.getClass();

			// TODO maybe set current deapth of invocation into linkRequest
			// It could happen that there is a call next. Then we either need to look deeper
			// in the Team hierarchy, or need to look deeper in the active bindings for that
			// joinpoint
			final List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					joinpointDescription);
			// TODO maybe set current deapth of invocation into linkRequest
			// Iterate over all bindings
			// Calculate composition of before and after bindings
			ListIterator<IBinding> reverseIterator = sortedCallinBindings.listIterator(sortedCallinBindings.size());
			while (reverseIterator.hasPrevious()) {
				final IBinding binding = reverseIterator.previous();

				switch (binding.getCallinModifier()) {
				case AFTER:
					final MethodHandle roleMethodHandle = lookup.findRoleMethod(binding, currentTeam);
					// Insert the parameters as prebound values into the role method
					// As we already now them.
					// https://docs.oracle.com/javase/9/docs/api/jdk/dynalink/DynamicLinkerFactory.html#setAutoConversionStrategy-jdk.dynalink.linker.MethodTypeConversionStrategy-
					// _OT$liftTo$... A convenient function of OTJ that takes a base class as
					// argument and returns the interface type of the role. This will be directly
					// invoked on the corresponding team.
					MethodHandle convertBaseToRoleObjectHandle = lookup.findVirtual(currentTeamClass,
							"_OT$liftTo$" + binding.getRoleClassName(), MethodType.methodType(
									lookup.getRoleType(binding, currentTeamClass, true), lookup.lookupClass()))
							.bindTo(currentTeam);
					// The role interface type needs to be cast to the role implementation type
					// RoleName -> __OT__RoleName.
					MethodHandle adaptedConvertBaseToRoleObjectHandle = convertBaseToRoleObjectHandle.asType(MethodType
							.methodType(lookup.getRoleType(binding, currentTeamClass, false), lookup.lookupClass()));
					// Lift the base object to its role object and insert it as first parameter as
					// we will call the role method on the result.
					MethodHandle convertBaseToRoleObject = MethodHandles.filterArguments(roleMethodHandle, 0,
							adaptedConvertBaseToRoleObjectHandle);
					MethodHandle dropUnnessecaryParameters = MethodHandles.dropArguments(convertBaseToRoleObject, 1,
							int.class, Object[].class);
					dropUnnessecaryParameters.asType(dropUnnessecaryParameters.type().changeReturnType(Object.class));

					if (resultHandle == null) {
						resultHandle = dropUnnessecaryParameters;
					} else {
						resultHandle = MethodHandles.foldArguments(resultHandle, dropUnnessecaryParameters);
					}
				default:
					break;
				}
			}
		}
		return Optional.ofNullable(resultHandle);
	}

	private Optional<MethodHandle> callReplace(final MethodType baseMethodType, final ITeam[] teams,
			final int[] callinIds, final String joinpointDescription, final Lookup lookup) {
		// Calculate all before bindings
		for (final ITeam currentTeam : teams) {
			final Class<?> currentTeamClass = currentTeam.getClass();

			// TODO maybe set current deapth of invocation into linkRequest
			// It could happen that there is a call next. Then we either need to look deeper
			// in the Team hierarchy, or need to look deeper in the active bindings for that
			// joinpoint
			final List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					joinpointDescription);
			// TODO maybe set current deapth of invocation into linkRequest
			// Iterate over all bindings
			// Calculate composition of before and after bindings
			for (final IBinding binding : sortedCallinBindings) {

				switch (binding.getCallinModifier()) {

				case REPLACE:
					final MethodHandle roleMethodHandle = lookup.findRoleMethod(binding, currentTeam);
					// Insert the parameters as prebound values into the role method
					// As we already now them.
					// https://docs.oracle.com/javase/9/docs/api/jdk/dynalink/DynamicLinkerFactory.html#setAutoConversionStrategy-jdk.dynalink.linker.MethodTypeConversionStrategy-
					MethodHandle reducedRoleMethodHandle = MethodHandles.insertArguments(roleMethodHandle, 2, teams, 0,
							callinIds);
					// _OT$liftTo$... A convenient function of OTJ that takes a base class as
					// argument and returns the interface type of the role. This will be directly
					// invoked on the corresponding team.
					MethodHandle convertBaseToRoleObjectHandle = lookup.findVirtual(currentTeamClass,
							"_OT$liftTo$" + binding.getRoleClassName(), MethodType.methodType(
									lookup.getRoleType(binding, currentTeamClass, true), lookup.lookupClass()))
							.bindTo(currentTeam);
					// The role interface type needs to be cast to the role implementation type
					// RoleName -> __OT__RoleName.
					MethodHandle adaptedConvertBaseToRoleObjectHandle = convertBaseToRoleObjectHandle.asType(MethodType
							.methodType(lookup.getRoleType(binding, currentTeamClass, false), lookup.lookupClass()));
					// Lift the base object to its role object and insert it as first parameter as
					// we will call the role method on the result.
					MethodHandle convertBaseToRoleObject = MethodHandles.filterArguments(reducedRoleMethodHandle, 0,
							adaptedConvertBaseToRoleObjectHandle);
					// A method type describing the base method with two preceding BaseClass
					// parameters.
					MethodType doubleBaseType = baseMethodType.insertParameterTypes(0, baseMethodType.parameterType(0));
					// TODO: Need to add the parameters of the role method
					if (reducedRoleMethodHandle.type().parameterCount() > 4) {
						final int spreadCount = reducedRoleMethodHandle.type().parameterCount() - 4;
						final int dropEnd = convertBaseToRoleObject.type().parameterCount();
						final int dropBegin = dropEnd - spreadCount;

						convertBaseToRoleObject = convertBaseToRoleObject.asSpreader(Object[].class, spreadCount);
						convertBaseToRoleObject = MethodHandles.permuteArguments(convertBaseToRoleObject,
								convertBaseToRoleObject.type().dropParameterTypes(dropBegin, dropEnd), 0, 1, 2, 3, 3);
					}
					// TODO: Maybe also need to add if there is a mapping
					// Cast the first two parameters from (BaseClass, IBoundBase2) -> (BaseClass,
					// BaseClass) as BaseClass implements IBoundBase2.
					// TODO: Implement as TypeConverter see
					// https://docs.oracle.com/javase/9/docs/api/jdk/dynalink/linker/GuardingTypeConverterFactory.html
					MethodHandle doubledFirstParamOfBaseMethodHandle = convertBaseToRoleObject.asType(doubleBaseType);
					// The base object needs to be given twice, as the first one will be converted
					// into the corresponding role object that is played by that base object.
					MethodHandle baseMethodEquivalentHandle = MethodHandles
							.permuteArguments(doubledFirstParamOfBaseMethodHandle, baseMethodType, 0, 0, 1, 2);
					return Optional.of(baseMethodEquivalentHandle);
				default:
					break;
				}
			}
		}
		return Optional.empty();
	}

	private Optional<MethodHandle> composeCallBefore(final MethodType baseMethodType, final ITeam[] teams,
			final int[] callinIds, final String joinpointDescription, final Lookup lookup) {

		MethodHandle resultHandle = null;

		for (final ITeam currentTeam : teams) {
			final Class<?> currentTeamClass = currentTeam.getClass();

			// TODO maybe set current deapth of invocation into linkRequest
			// It could happen that there is a call next. Then we either need to look deeper
			// in the Team hierarchy, or need to look deeper in the active bindings for that
			// joinpoint
			final List<IBinding> sortedCallinBindings = TeamManager.getPrecedenceSortedCallinBindings(currentTeam,
					joinpointDescription);
			// TODO maybe set current deapth of invocation into linkRequest
			// Iterate over all bindings
			// Calculate composition of before and after bindings
			for (final IBinding binding : sortedCallinBindings) {

				switch (binding.getCallinModifier()) {
				case BEFORE:
					final MethodHandle roleMethodHandle = lookup.findRoleMethod(binding, currentTeam);
					// Insert the parameters as prebound values into the role method
					// As we already now them.
					// https://docs.oracle.com/javase/9/docs/api/jdk/dynalink/DynamicLinkerFactory.html#setAutoConversionStrategy-jdk.dynalink.linker.MethodTypeConversionStrategy-
					// _OT$liftTo$... A convenient function of OTJ that takes a base class as
					// argument and returns the interface type of the role. This will be directly
					// invoked on the corresponding team.
					MethodHandle convertBaseToRoleObjectHandle = lookup.findVirtual(currentTeamClass,
							"_OT$liftTo$" + binding.getRoleClassName(), MethodType.methodType(
									lookup.getRoleType(binding, currentTeamClass, true), lookup.lookupClass()))
							.bindTo(currentTeam);
					// The role interface type needs to be cast to the role implementation type
					// RoleName -> __OT__RoleName.
					MethodHandle adaptedConvertBaseToRoleObjectHandle = convertBaseToRoleObjectHandle.asType(MethodType
							.methodType(lookup.getRoleType(binding, currentTeamClass, false), lookup.lookupClass()));
					// Lift the base object to its role object and insert it as first parameter as
					// we will call the role method on the result.
					MethodHandle convertBaseToRoleObject = MethodHandles.filterArguments(roleMethodHandle, 0,
							adaptedConvertBaseToRoleObjectHandle);
					MethodHandle dropUnnessecaryParameters = MethodHandles.dropArguments(convertBaseToRoleObject, 1,
							int.class, Object[].class);
					dropUnnessecaryParameters.asType(dropUnnessecaryParameters.type().changeReturnType(Object.class));

					if (resultHandle == null) {
						resultHandle = dropUnnessecaryParameters;
					} else {
						resultHandle = MethodHandles.foldArguments(resultHandle, dropUnnessecaryParameters);
					}
				default:
					break;
				}
			}
		}
		return Optional.ofNullable(resultHandle);
	}

	private MethodHandle callOrig(final Lookup lookup, final MethodType baseMethodType) {
		return lookup.findOwnVirtual("_OT$callOrig", baseMethodType.dropParameterTypes(0, 1));
	}

}
