package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;

public class Linker {

	private final static String RELINK_METHOD_NAME = "relink";

	private LinkerService linkerService;

	public Linker(LinkerService linkerService) {
		this.linkerService = linkerService;

	}

	public CallinCallSite link(CallinCallSite callsite) {
		callsite.initialize(createRelinkAndInvokeMethod(callsite));
		return callsite;
	}

	private final static MethodHandle RELINK = Lookup.findOwnSpecial(MethodHandles.lookup(), RELINK_METHOD_NAME,
			MethodHandle.class, CallinCallSite.class, Object[].class);

	private MethodHandle createRelinkAndInvokeMethod(CallinCallSite callsite) {
		final MethodHandle boundRelinker = MethodHandles.insertArguments(RELINK, 0, this, callsite);
		// Gather all arguments into an Object[]
		final MethodType type = callsite.getDescriptor().getType();
		final MethodHandle collectingRelinker = boundRelinker.asCollector(Object[].class, type.parameterCount());
		return MethodHandles.foldArguments(MethodHandles.exactInvoker(type),
				collectingRelinker.asType(type.changeReturnType(MethodHandle.class)));
	}

	@SuppressWarnings("unused")
	private MethodHandle relink(CallinCallSite callsite, Object... args) {
		final CallSiteDescriptor descriptor = callsite.getDescriptor();
		final LinkRequest linkRequest = new LinkRequest(descriptor, args);

		final GuardedInvocation guardedInvocation = linkerService.getGuardedInvocation(linkRequest);
		callsite.relink(guardedInvocation, createRelinkAndInvokeMethod(callsite));

		return guardedInvocation.getInvocation();
	}

	public static Linker createLinker() {
		return new Linker(new LinkerService());
	}

}
