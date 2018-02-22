/**
 * 
 */
package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.objectteams.otredyn.runtime.ClassIdentifierProviderFactory;
import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.IBoundTeam;
import org.eclipse.objectteams.otredyn.runtime.IClassIdentifierProvider;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.objectteams.IBoundBase2;
import org.objectteams.ITeam;

/**
 * @author Lars Schütze
 *
 */
public class CallinCallSite extends MutableCallSite {

	private final CallSiteDescriptor descriptor;

	public CallinCallSite(CallSiteDescriptor descriptor) {
		super(descriptor.getType());

		this.descriptor = descriptor;
	}

	public void initialize(MethodHandle target) {
		setTarget(target);
	}

	public void relink(GuardedInvocation guardedInvocation, MethodHandle fallback) {
		setTarget(guardedInvocation.compose(fallback));
	}

	public CallSiteDescriptor getDescriptor() {
		return descriptor;
	}
}
