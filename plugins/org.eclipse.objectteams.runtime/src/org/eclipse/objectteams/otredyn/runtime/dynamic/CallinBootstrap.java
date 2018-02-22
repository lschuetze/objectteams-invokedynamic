package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class CallinBootstrap {

	private final static Linker linker = Linker.createLinker();

	public final static MethodType BOOTSTRAP_METHOD_TYPE = MethodType.methodType(CallSite.class,
			MethodHandles.Lookup.class, String.class, MethodType.class, String.class);

	public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type,
			String joinpointDescription) {
		final CallSiteDescriptor descriptor = new CallSiteDescriptor(caller, name, type, joinpointDescription);
		return linker.link(new CallinCallSite(descriptor));
	}

}
