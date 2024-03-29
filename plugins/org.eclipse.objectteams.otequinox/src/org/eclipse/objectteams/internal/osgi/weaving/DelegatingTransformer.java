/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 * 
 * Copyright 2015, 2016 GK Software AG
 *  
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.objectteams.org for updates and contact.
 * 
 * Contributors:
 * 	Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.internal.osgi.weaving;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.objectteams.internal.osgi.weaving.OTWeavingHook.WeavingReason;
import org.eclipse.objectteams.internal.osgi.weaving.OTWeavingHook.WeavingScheme;
import org.eclipse.objectteams.internal.osgi.weaving.Util.ProfileKind;
import org.eclipse.objectteams.otredyn.bytecode.IRedefineStrategy;
import org.eclipse.objectteams.otredyn.bytecode.RedefineStrategyFactory;
import org.eclipse.objectteams.otredyn.transformer.IWeavingContext;
import org.eclipse.objectteams.runtime.DebugHooks;
import org.eclipse.objectteams.runtime.IReweavingTask;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Generalization over the transformers of OTRE and OTDRE.
 */
public abstract class DelegatingTransformer {

	static final String OT_EQUINOX_DEBUG_AGENT = "org.eclipse.objectteams.otequinox.OTEquinoxAgent";

	@SuppressWarnings("serial")
	public static class OTAgentNotInstalled extends Exception {
		OTAgentNotInstalled() {
			super("Agent class "+DelegatingTransformer.OT_EQUINOX_DEBUG_AGENT+" was not installed. OT/Equinox will be desabled.\n" +
					"If this happens during the restart after installing OT/Equinox, please exit Eclipse and perform a fresh start.");
		}
	}

	static void checkAgentAvailability(WeavingScheme weavingScheme) throws OTAgentNotInstalled {
		if (weavingScheme == WeavingScheme.OTDRE) {
			try {
				ClassLoader.getSystemClassLoader().loadClass(DelegatingTransformer.OT_EQUINOX_DEBUG_AGENT);
			} catch (ClassNotFoundException cnfe) {
				throw new OTAgentNotInstalled();
			}
		}
	}

	/** Factory method for a fresh transformer. */
	static @NonNull DelegatingTransformer newTransformer(WeavingScheme weavingScheme, OTWeavingHook hook, BundleWiring wiring) {
		switch (weavingScheme) {
			case OTDRE:
				return new OTDRETransformer(getWeavingContext(hook, wiring));
			case OTRE:
				return new OTRETransformer();
			default:
				throw new NullPointerException("WeavingScheme must be defined");
		}
	}
	
	private static class OTRETransformer extends DelegatingTransformer {
		org.eclipse.objectteams.otre.jplis.ObjectTeamsTransformer transformer = new org.eclipse.objectteams.otre.jplis.ObjectTeamsTransformer();
		@Override
		public void readOTAttributes(String className, InputStream inputStream, String fileName, Bundle bundle) throws ClassFormatError, IOException {
			this.transformer.readOTAttributes(inputStream, fileName, bundle);
		}
		public Collection<String> fetchAdaptedBases() {
			return this.transformer.fetchAdaptedBases();
		}
		public byte[] transform(Bundle bundle, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytes)
				throws IllegalClassFormatException
		{
			return this.transformer.transform(bundle, className, classBeingRedefined, protectionDomain, bytes);
		}
	}
	
	private static class OTDRETransformer extends DelegatingTransformer {
		org.eclipse.objectteams.otredyn.transformer.jplis.ObjectTeamsTransformer transformer;
		public OTDRETransformer(IWeavingContext weavingContext) {
			RedefineStrategyFactory.setRedefineStrategy(new OTEquinoxRedefineStrategy());
			transformer = new org.eclipse.objectteams.otredyn.transformer.jplis.ObjectTeamsTransformer(weavingContext);
		}
		@Override
		public void readOTAttributes(String className, InputStream inputStream, String fileName, Bundle bundle) throws ClassFormatError, IOException {
			// TODO provide classID
			this.transformer.readOTAttributes(className, className.replace('.', '/'), inputStream, getBundleLoader(bundle));
		}
		public Collection<String> fetchAdaptedBases() {
			return transformer.fetchAdaptedBases();
		}
		public byte[] transform(final Bundle bundle, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
			// TODO provide classID
			return transformer.transform(getBundleLoader(bundle), className, className.replace('.', '/'), classBeingRedefined, bytes);
		}
	}

	/** Enable OTDRE to use the OTEquinoxAgent, if present, for class redefinition. */
	private static class OTEquinoxRedefineStrategy implements IRedefineStrategy {

		public void redefine(Class<?> clazz, byte[] bytecode) throws ClassNotFoundException, UnmodifiableClassException {
			ClassDefinition arr_cd[] = { new ClassDefinition(clazz, bytecode) };
			try {
				long start = System.nanoTime();
				reflectivelyInvoke(arr_cd);
				if (Util.PROFILE) Util.profile(start, ProfileKind.RedefineClasses, clazz.getName());
				DebugHooks.afterRedefineClasses(clazz.getName());
			} catch (ClassFormatError|UnmodifiableClassException e) {
				// error output during redefinition tends to swallow the stack, print it now:
				System.err.println("Error redefining "+clazz.getName());
				e.printStackTrace();
				throw e;
			}
		}
		
		static void reflectivelyInvoke(ClassDefinition[] definitions) throws ClassNotFoundException, ClassFormatError, UnmodifiableClassException {
			try {
				Class<?> agentClass = ClassLoader.getSystemClassLoader().loadClass(OT_EQUINOX_DEBUG_AGENT);
				java.lang.reflect.Method redefine = agentClass.getMethod("redefine", new Class<?>[]{ClassDefinition[].class});
				redefine.invoke(null, new Object[]{definitions});
			} catch (InvocationTargetException ite) {
				Throwable cause = ite.getCause();
				if (cause instanceof ClassFormatError)
					throw (ClassFormatError)cause;
				if (cause instanceof UnmodifiableClassException)
					throw (UnmodifiableClassException)cause;
				throw new UnmodifiableClassException(cause.getClass().getName()+": "+cause.getMessage());
			} catch (ClassNotFoundException cnfe) {
				throw cnfe;
			} catch (Throwable t) {
				throw new UnmodifiableClassException(t.getClass().getName()+": "+t.getMessage());
			}
		}
	}

	static ClassLoader getBundleLoader(final Bundle bundle) {
		return new ClassLoader() {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				return bundle.loadClass(name);
			}
			@Override
			public URL getResource(String name) {
				return bundle.getResource(name);
			}
		};
	}

	static IWeavingContext getWeavingContext(final OTWeavingHook hook, final BundleWiring bundleWiring) {
		return new IWeavingContext() {
			@Override
			public boolean isWeavable(String className) {
				return isWeavable(className, false);
			}
			@Override
			public boolean isWeavable(String className, boolean considerSupers) {
				return className != null && hook.requiresWeaving(bundleWiring, className, null, considerSupers) != WeavingReason.None;
			}
			
			@Override
			public boolean scheduleReweaving(String className, IReweavingTask task) {
				return hook.scheduleReweaving(className, task);
			}
		};
	}
	
	public abstract void readOTAttributes(String className, InputStream inputStream, String fileName, Bundle bundle) throws ClassFormatError, IOException;
	
	public abstract byte[] transform(Bundle bundle, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytes)
			throws IllegalClassFormatException;

	public abstract Collection<String> fetchAdaptedBases();
}
