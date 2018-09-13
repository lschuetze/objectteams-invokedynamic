package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.eclipse.objectteams.otredyn.runtime.dynamic.linker.GuardingDynamicCallinLinker;
import org.objectteams.IBoundBase2;
import org.objectteams.ITeam;

import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.support.ChainedCallSite;

public class CallinBootstrap {

	public final static MethodType BOOTSTRAP_METHOD_TYPE = MethodType.methodType(CallSite.class,
			MethodHandles.Lookup.class, String.class, MethodType.class, String.class, int.class);

	private static final GuardingDynamicLinker callinLinker = new GuardingDynamicCallinLinker();
	private static final DynamicLinker dynamicLinker = createDynamicLinker();

	private static DynamicLinker createDynamicLinker() {
		DynamicLinkerFactory factory = new DynamicLinkerFactory();
		factory.setPrioritizedLinker(callinLinker);
		// factory.setAutoConversionStrategy(new
		// DynamicObjectTeamsConversionStrategy());
		return factory.createLinker();
	}

	/**
	 * 
	 * @param lookup               The lookup object for the base callin or in case
	 *                             of callnext the lookup object of the role class
	 * @param name
	 * @param type
	 * @param joinpointDescription
	 * @return
	 */
	public static CallSite callAllBindings(MethodHandles.Lookup lookup, String name, MethodType type, String joinpointDescr,
			int flags) {
		return dynamicLinker
				.link(new ChainedCallSite(DynamicCallSiteDescriptor.get(lookup, name, type, joinpointDescr, flags)));
	}
	
	public static CallSite callNext(MethodHandles.Lookup lookup, String name, MethodType type,
			IBoundBase2 baseArg, ITeam[] teams, int index, int[] callinIds, int bmId, Object[] args, Object[] boxedArgs, int basecallFlag) {
		return null;
	}

}
