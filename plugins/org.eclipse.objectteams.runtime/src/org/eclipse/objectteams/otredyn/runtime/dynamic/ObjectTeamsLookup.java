package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.objectteams.ITeam;

public class Lookup {
	private final static HashMap<String, MethodType> MT_ROLE_CACHE = new HashMap<>();
	private final MethodHandles.Lookup lookup;

	public Lookup(MethodHandles.Lookup lookup) {
		this.lookup = lookup;
	}

	public Class<?> lookupClass() {
		return lookup.lookupClass();
	}

	public static MethodHandle findOwnSpecial(MethodHandles.Lookup lookup, String name, Class<?> rtype,
			Class<?>... ptypes) {
		return new Lookup(lookup).findOwnSpecial(name, rtype, ptypes);
	}

	public MethodHandle findOwnSpecial(String name, Class<?> rtype, Class<?>... ptypes) {
		return findSpecial(lookup.lookupClass(), name, MethodType.methodType(rtype, ptypes));
	}

	public MethodHandle findSpecial(Class<?> declaringClass, String name, MethodType type) {
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

	public MethodHandle findOwnVirtual(String name, MethodType type) {
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

	public MethodHandle findVirtual(Class<?> declaringClass, String name, MethodType type) {
		// TODO error
		try {
			return lookup.findVirtual(declaringClass, name, type);
		} catch (NoSuchMethodException e) {
			// Try super
			try {
				return lookup.findVirtual(declaringClass.getSuperclass(), name, type);
			} catch (NoSuchMethodException ex) {
				NoSuchMethodError ee = new NoSuchMethodError("Class " + declaringClass + " and " + declaringClass.getSuperclass() + ", name " + name + " , type " + type.toMethodDescriptorString());
				ee.initCause(e);
				throw ee;
			} catch (IllegalAccessException ie) {
				IllegalAccessError ee = new IllegalAccessError(ie.getMessage() + " declaringClass " + declaringClass);
				ee.initCause(ie);
				throw ee;
			}
		} catch (IllegalAccessException e) {
			IllegalAccessError ee = new IllegalAccessError(e.getMessage());
			ee.initCause(e);
			throw ee;
		}
	}

	public Class<?> getRoleType(IBinding binding, Class<?> teamClass, boolean itf) {
		final String roleClassName = new StringBuilder(teamClass.getName()).append(itf ? "$" : "$__OT__")
				.append(binding.getRoleClassName()).toString().intern();
		try {
			return Class.forName(roleClassName, false, teamClass.getClassLoader());
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}

	public MethodHandle findRoleMethod(IBinding binding, ITeam team) {
		final Class<?> roleType = getRoleType(binding, team.getClass(), false);

		final MethodType mt = MethodType.fromMethodDescriptorString(binding.getRoleMethodSignature(), roleType.getClassLoader());
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

	public MethodHandle findOrig(MethodType baseMethodType) throws NoSuchMethodException, IllegalAccessException {
		Class<?> baseClass = lookup.lookupClass();
		return lookup.findVirtual(baseClass, "_OT$callOrig", baseMethodType.dropParameterTypes(0, 1));
	}

}
