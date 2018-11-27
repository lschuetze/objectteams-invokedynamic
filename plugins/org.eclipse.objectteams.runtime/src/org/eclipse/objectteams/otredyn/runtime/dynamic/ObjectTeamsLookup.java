package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.dynamic.linker.support.ObjectTeamsTypeUtilities;
import org.objectteams.ITeam;

public class ObjectTeamsLookup {
	
	private static final MethodType CallOrigType = MethodType.methodType(Object.class, int.class, Object[].class);

	public static MethodHandle findOwnSpecial(MethodHandles.Lookup lookup, String name, Class<?> rtype,
			Class<?>... ptypes) {
		return findSpecial(lookup, lookup.lookupClass(), name, MethodType.methodType(rtype, ptypes));
	}

	public static MethodHandle findSpecial(MethodHandles.Lookup lookup, Class<?> declaringClass, String name,
			MethodType type) {
		try {
			return lookup.findSpecial(declaringClass, name, type, declaringClass);
		} catch (NoSuchMethodException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		} catch (IllegalAccessException e) {
			IllegalAccessError ee = new IllegalAccessError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static MethodHandle findOwnVirtual(MethodHandles.Lookup lookup, String name, MethodType type) {
		try {
			return lookup.findVirtual(lookup.lookupClass(), name, type);
		} catch (NoSuchMethodException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		} catch (IllegalAccessException e) {
			IllegalAccessError ee = new IllegalAccessError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static MethodHandle findVirtual(MethodHandles.Lookup lookup, Class<?> declaringClass, String name,
			MethodType type) {
		// TODO error
		try {
			return lookup.findVirtual(declaringClass, name, type);
		} catch (NoSuchMethodException e) {
			NoSuchMethodError ee = new NoSuchMethodError("Class " + declaringClass + " and "
					+ declaringClass.getSuperclass() + ", name " + name + " , type " + type.toMethodDescriptorString());
			ee.initCause(e);
			throw ee;
		} catch (IllegalAccessException e) {
			IllegalAccessError ee = new IllegalAccessError(e.getMessage());
			ee.initCause(e);
			throw ee;
		}
	}

	public static MethodHandle findRoleMethod(MethodHandles.Lookup lookup, IBinding binding, ITeam team) {
		final Class<?> roleType = ObjectTeamsTypeUtilities.getRoleImplementationType(binding.getRoleClassName(),
				team.getClass());

		final MethodType mt = MethodType.fromMethodDescriptorString(binding.getRoleMethodSignature(),
				roleType.getClassLoader());
		try {
			return lookup.findVirtual(roleType, binding.getRoleMethodName(), mt);
		} catch (NoSuchMethodException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		} catch (IllegalAccessException e) {
			IllegalAccessError ee = new IllegalAccessError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static MethodHandle findOrig(MethodHandles.Lookup lookup, Class<?> baseClass, MethodType baseMethodType) {
		try {
			return lookup.findVirtual(baseClass, "_OT$callOrig", CallOrigType);
		} catch (NoSuchMethodException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		} catch (IllegalAccessException e) {
			IllegalAccessError ee = new IllegalAccessError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static MethodHandle findLifting(MethodHandles.Lookup lookup, IBinding binding, Class<?> teamClass) {
		String liftingMethod = ("_OT$liftTo$" + binding.getRoleClassName()).intern();
		Class<?> baseClass;
		try {
			baseClass = Class.forName(binding.getBoundClass().replace('/', '.'));
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
		MethodHandle convertBaseToRoleObjectHandle = findVirtual(lookup, teamClass, liftingMethod,
				MethodType.methodType(
						ObjectTeamsTypeUtilities.getRoleInterfaceType(binding.getRoleClassName(), teamClass),
						baseClass));

		MethodHandle adaptedConvertBaseToRoleObjectHandle = convertBaseToRoleObjectHandle.asType(MethodType.methodType(
				ObjectTeamsTypeUtilities.getRoleImplementationType(binding.getRoleClassName(), teamClass), teamClass,
				baseClass));

		return adaptedConvertBaseToRoleObjectHandle;
	}

}
