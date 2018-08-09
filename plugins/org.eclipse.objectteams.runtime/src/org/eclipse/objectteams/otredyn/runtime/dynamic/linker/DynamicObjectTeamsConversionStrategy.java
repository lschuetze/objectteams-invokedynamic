package org.eclipse.objectteams.otredyn.runtime.dynamic.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import jdk.dynalink.linker.MethodTypeConversionStrategy;

public class DynamicObjectTeamsConversionStrategy implements MethodTypeConversionStrategy {

	@Override
	public MethodHandle asType(MethodHandle target, MethodType newType) {
		return null;
	}
}
