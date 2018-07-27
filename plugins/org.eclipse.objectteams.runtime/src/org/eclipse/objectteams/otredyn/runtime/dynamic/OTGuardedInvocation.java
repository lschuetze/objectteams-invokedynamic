package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;

/**
 * 
 * @author Lars Schütze
 *
 */
public class GuardedInvocation {
	private static final SwitchPoint[] NO_SWITCHPOINTS = new SwitchPoint[0];

	private SwitchPoint[] switchpoints;
	private MethodHandle invocation;

	GuardedInvocation(MethodHandle invocation) {
		// resolve amiguity
		this(invocation, (SwitchPoint) null);
	}

	GuardedInvocation(MethodHandle invocation, SwitchPoint switchpoint) {
		this.switchpoints = switchpoint == null ? NO_SWITCHPOINTS : new SwitchPoint[] { switchpoint };
		this.invocation = invocation;
	}

	GuardedInvocation(MethodHandle invocation, SwitchPoint[] switchpoints) {
		this.invocation = invocation;
		this.switchpoints = switchpoints != null && switchpoints.length > 0 ? switchpoints.clone() : NO_SWITCHPOINTS;
	}

	MethodHandle compose(MethodHandle switchpointFallback) {
		MethodHandle spGuarded = invocation;
		for (SwitchPoint sp : switchpoints) {
			spGuarded = sp.guardWithTest(spGuarded, switchpointFallback);
		}
		return spGuarded;
	}

	public MethodHandle getInvocation() {
		return invocation;
	}

	public GuardedInvocation addSwitchPoint(SwitchPoint switchPoint) {
		if (switchPoint == null) {
			return this;
		}

		SwitchPoint[] newSwitchPoints = new SwitchPoint[switchpoints.length + 1];
		System.arraycopy(switchpoints, 0, newSwitchPoints, 0, switchpoints.length);
		newSwitchPoints[switchpoints.length] = switchPoint;

		return new GuardedInvocation(invocation, newSwitchPoints);
	}

}
