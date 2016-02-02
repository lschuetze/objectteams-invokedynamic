/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2016 Oliver Frank and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors:
 *		Oliver Frank - Initial API and implementation
 *		Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otredyn.bytecode.asm;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.objectteams.otredyn.bytecode.AbstractBoundClass;
import org.eclipse.objectteams.otredyn.bytecode.AbstractTeam;
import org.eclipse.objectteams.otredyn.bytecode.Field;
import org.eclipse.objectteams.otredyn.bytecode.IBytecodeProvider;
import org.eclipse.objectteams.otredyn.bytecode.Method;
import org.eclipse.objectteams.otredyn.bytecode.RedefineStrategyFactory;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.eclipse.objectteams.otredyn.transformer.names.ClassNames;
import org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * This class implements the bytecode manipulating part of {@link AbstractBoundClass}.
 * It uses ASM to manipulate the bytecode.
 * @author Oliver Frank
 */
class AsmWritableBoundClass extends AsmBoundClass {
	private static boolean dumping = false;

    static {
        if(System.getProperty("ot.dump")!=null)
            dumping = true;
    }
    
	private ClassWriter writer;
	private MultiClassAdapter multiAdapter;
	private ClassReader reader;
	private boolean isTransformed;
	private boolean isTransformedForMemberAccess;
	private boolean isTransformedStatic;
	private List<AbstractTransformableClassNode> nodes;
	private boolean isFirstTransformation = true;

	private boolean isTransformationActive;

	protected AsmWritableBoundClass(String name, String id, IBytecodeProvider bytecodeProvider, ClassLoader loader) {
		super(name, id, bytecodeProvider, loader);
	}
	
	/**
	 * Adds a field to the class
	 * 
	 * @param field defines name and type of the field
	 * @param access access flags for the field
	 * @see AddFieldAdapter
	 */
	private void addField(Field field, int access) {
		assert (isTransformationActive) : "No transformation active";
		String desc = field.getSignature();
		multiAdapter.addVisitor(new AddFieldAdapter(writer, field.getName(), access, desc));
	}

	/**
	 * Adds an empty method to the class
	 * @param method
	 * @param access
	 * @param signature
	 * @param exceptions
	 * @param superToCall may be null, else the super class to which a super-call should be inserted
	 * @see AddEmptyMethodAdapter
	 */
	private void addEmptyMethod(Method method, int access, String signature, String[] exceptions, String superToCall) {
		assert (isTransformationActive) : "No transformation active";
		String desc = method.getSignature();
		Type[] args = Type.getArgumentTypes(desc);
		multiAdapter.addVisitor(new AddEmptyMethodAdapter(writer, method.getName(),
				access, desc, exceptions, signature, args.length + 1, superToCall));
	}

	/**
	 * Adds an interface to the class
	 * @see AddInterfaceAdapter
	 * @param name
	 */
	private void addInterface(String name) {
		assert (isTransformationActive) : "No transformation active";
		multiAdapter.setToplevelVisitor(new AddInterfaceAdapter(writer, name));
	}

	/**
	 * This method must be called before any transformation
	 * can be done. It makes it possible to collect all transformations
	 * and transform the bytecode only one time at the end.
	 */
	@Override
	protected void startTransformation() {
		
		reader = new ClassReader(allocateAndGetBytecode());

		writer = getClassWriter();
		multiAdapter = new MultiClassAdapter(writer);
		nodes = new ArrayList<AbstractTransformableClassNode>();
		isTransformationActive = true;
	}

	LoaderAwareClassWriter getClassWriter() {
		int flags = ClassWriter.COMPUTE_FRAMES;
// DEBUG: when frame computation throws an exception, enable dumping of class file without frames computed:
//		if (getName().contains("JUnitLaunchConfigurationDelegate"))
//			flags = 0;
		return new LoaderAwareClassWriter(reader, flags, this.loader);
	}
	
	/**
	 * Is the class manipulated right now.
	 */
	@Override
	public boolean isTransformationActive() {
		return isTransformationActive;
	}

	/**
	 * Executes all pending transformations.
	 */
	@Override
	protected void endTransformation(final Class<?> definedClass) {
		assert (isTransformationActive) : "No transformation active";
		
		if (multiAdapter == null || nodes == null)
			return;
		if (multiAdapter.hasVisitors() || !nodes.isEmpty()) {
			// //TODO (ofra): Do everything in one transformation
			// Do all transformation with the Core API of ASM
//			try {
				reader.accept(multiAdapter, ClassReader.SKIP_FRAMES);
//			} catch (RuntimeException e) {
//				System.err.println("Cannot transform class "+this+":");
//				e.printStackTrace();
//				return;
//			}
			setBytecode(writer.toByteArray());
			//Do all transformations with the Tree API of ASM
			for (AbstractTransformableClassNode node : nodes) {
				reader = new ClassReader(allocateAndGetBytecode());
				reader.accept(node, ClassReader.SKIP_FRAMES);
				if (node.transform()) {
					writer = getClassWriter();
					node.accept(writer);
					setBytecode(writer.toByteArray());
				}
			}
			
			dump();
			reader = null;
			writer = null;
			multiAdapter = null;
			nodes = null;
			//Check, if this is the first transformation for this class
			if (!this.isFirstTransformation) {
				// It is not the first transformation, so redefine the class
				try {
					redefine(definedClass);
				} catch (Throwable t) {
	//				t.printStackTrace(System.out);
					// if redefinition failed (ClassCircularity?) install a runnable for deferred redefinition:
					final Runnable previousTask = TeamManager.pendingTasks.get();
					TeamManager.pendingTasks.set(new Runnable() {
						public void run() {
							if (previousTask != null)
								previousTask.run();
							redefine(definedClass);
						}
						@Override
						public String toString() {
							return "Retry "+AsmWritableBoundClass.this.toString();
						}
					});
					// not done, only partial cleanup:
					isTransformationActive = false;
					isFirstTransformation = false;
					return;
				}
			}
		} else {
			reader = null;
			writer = null;
			multiAdapter = null;
			nodes = null;
		}
		isTransformationActive = false;
		isFirstTransformation = false;
		releaseBytecode();
	}

	protected void superTransformation(Class<?> definedClass) {		
		AbstractTeam mySuper = getSuperclass();
		if (mySuper != null && mySuper.isLoaded()) {
			boolean superNeedsWeaving = false;
			synchronized (mySuper.openBindingTasks) {
				superNeedsWeaving = !mySuper.openBindingTasks.isEmpty();
			}
			if (superNeedsWeaving) // no locks held during the following call!
				mySuper.handleTaskList(definedClass != null ? definedClass.getSuperclass() : null);
		}
	}

	/**
	 * Creates the dispatch code in the original method.
	 * @see CreateDispatchCodeInOrgMethodAdapter
	 */
	@Override
	protected void createDispatchCodeInOrgMethod(Method boundMethod,
			int joinPointId, int boundMethodId) {
		assert (isTransformationActive) : "No transformation active";
		nodes.add(new CreateDispatchCodeInOrgMethodAdapter(boundMethod,
				joinPointId, boundMethodId));
	}

	/**
	 * Creates the dispatch code in the method callAllBindings.
	 * @see CreateDispatchCodeInCallAllBindingsAdapter
	 */
	@Override
	protected void createDispatchCodeInCallAllBindings(int joinpointId,
			int boundMethodId) {
		assert (isTransformationActive) : "No transformation active";
		nodes.add(new CreateDispatchCodeInCallAllBindingsAdapter(joinpointId,
				boundMethodId));
	}

	/**
	 * Moves the code of the original method to callOrig or callOrigStatic.
	 * @see MoveCodeToCallOrigAdapter
	 */
	@Override
	protected void moveCodeToCallOrig(Method boundMethod, int boundMethodId) {
		if (boundMethod.getName().equals("<init>")) return; // don't move constructor code
		assert (isTransformationActive) : "No transformation active";
		nodes.add(new MoveCodeToCallOrigAdapter(this, boundMethod, boundMethodId, this.weavingContext));
	}
	
	/**
	 * Creates a super call in callOrig.
	 * @see CreateSuperCallInCallOrigAdapter
	 */
	@Override
	protected void createSuperCallInCallOrig(int boundMethodId) {
		assert (isTransformationActive) : "No transformation active";
		nodes.add(new CreateSuperCallInCallOrigAdapter(
				getInternalSuperClassName(), boundMethodId));

	}

	/**
	 * Creates a call of callAllBindings in the original method.
	 * @see CreateCallAllBindingsCallInOrgMethod
	 */
	@Override
	protected void createCallAllBindingsCallInOrgMethod(Method boundMethod,
			int boundMethodId, boolean needToAddMethod) {
		assert (isTransformationActive) : "No transformation active";
		if (needToAddMethod) {
			String desc = boundMethod.getSignature();
			Type[] args = Type.getArgumentTypes(desc);
			multiAdapter.addVisitor(new AddEmptyMethodAdapter(writer, boundMethod.getName(),
					boundMethod.getAccessFlags(), desc, null, boundMethod.getSignature(), args.length+1/*maxLocals*/, null));
		}
		nodes.add(new CreateCallAllBindingsCallInOrgMethod(boundMethod,
				boundMethodId));

	}

	@Override
	protected void replaceWickedSuperCalls(AbstractBoundClass superclass, Method targetMethod) {
		ReplaceWickedSuperCallsAdapter.register(nodes, superclass, targetMethod);
	}

	/**
	 * Prepares a the class for a decapsulation of one of its methods
	 */
	@Override
	protected void weaveMethodAccess(Method method, int accessId) {
		nodes.add(new CreateMethodAccessAdapter(method, accessId));

	}

	/**
	 * Prepares a the class for a decapsulation of one of its fields
	 */
	@Override
	protected void weaveFieldAccess(Field field, int accessId) {
		nodes.add(new CreateFieldAccessAdapter(field, accessId));

	}

	/**
	 * Write the bytecode in the directory ./otdyn to the hard disk, 
	 * if the system property "ot.dump" is set.
	 */
	private void dump() {
		if (!dumping)
			return;
		
		FileOutputStream fos = null;
		try {
			String name = getName().replaceAll("/", ".");
			File dir = new File("otdyn");
			if (!dir.exists())
				dir.mkdir();
			String filename = "otdyn/" + name + ".class";
			fos = new FileOutputStream(filename);
			fos.write(allocateAndGetBytecode());
			fos.close();
		} catch (Exception e) {
			// TODO (ofra): Log error while dumping
			e.printStackTrace();
		} 
	}

	private int n = 0; // counts dump files for this class
	public void dump(byte[] bytecode, String postfix) {
		if (!dumping)
			return;

		FileOutputStream fos = null;
		try {
			String name = getName().replaceAll("/", ".");
			File dir = new File("otdyn");
			if (!dir.exists())
				dir.mkdir();
			String filename = "otdyn/" + name + postfix+".#"+(n++);
			fos = new FileOutputStream(filename);
			fos.write(bytecode);
			fos.close();
		} catch (Exception e) {
			// TODO (ofra): Log error while dumping
			e.printStackTrace();
		}
	}

	/**
	 * Redefines the class
	 * @param definedClass previously defined class if available
	 */
	private void redefine(Class<?> definedClass) {
		try {
			Class<?> clazz = definedClass != null ? definedClass : this.loader.loadClass(this.getName()); // boot classes may have null classloader, can't be redefined anyway?
			byte[] bytecode = allocateAndGetBytecode();
			dump(bytecode, "redef");
			RedefineStrategyFactory.getRedefineStrategy().redefine(clazz, bytecode);
		} catch (Throwable t) {
			throw new RuntimeException("Error occured while dynamically redefining class " + getName()+"\n"+t.getMessage(), t);
		}
	}

	/**
	 * Do all transformations needed at load time
	 */
	@Override
	protected void prepareAsPossibleBaseClass() {
		if (!isFirstTransformation)
			return;

		addInterface(ClassNames.I_BOUND_BASE_SLASH);
		
		int methodModifiers = Opcodes.ACC_PUBLIC;
		if (isInterface())
			methodModifiers |= Opcodes.ACC_ABSTRACT;
		
		String superClassName = getSuperClassName();
		boolean superIsWeavable = weavingContext.isWeavable(superClassName);
		String internalSuperClassName = superIsWeavable ? getInternalSuperClassName() : null;

		if (!isInterface() && !superIsWeavable)
			addField(ConstantMembers.roleSet, Opcodes.ACC_PUBLIC);

		
		addEmptyMethod(ConstantMembers.callOrig, methodModifiers, null, null, internalSuperClassName);
		addEmptyMethod(ConstantMembers.callAllBindingsClient, methodModifiers, null, null, internalSuperClassName);
		
		// the methods callOrigStatic and accessStatic have to already exist to call it in a concrete team
		if (!isInterface()) {
			addEmptyMethod(getCallOrigStatic(), Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, null, null, null);
			addEmptyMethod(ConstantMembers.accessStatic, Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, null, null, null);
		}
		addEmptyMethod(ConstantMembers.access, methodModifiers, null, null, internalSuperClassName);
		addEmptyMethod(ConstantMembers.addOrRemoveRole, methodModifiers, null, null, internalSuperClassName);
		
		if (!isInterface())
			multiAdapter.addVisitor(new AddAfterClassLoadingHook(this.writer, this));

		if (AddThreadNotificationAdapter.shouldNotify(this))
			multiAdapter.addVisitor(new AddThreadNotificationAdapter(this.writer, this));
	}

	/** Get the suitable variant of _OT$callOrigStatic, respecting synth args for static role methods. */
	Method getCallOrigStatic() {
		if (isRole())
			return ConstantMembers.callOrigStaticRoleVersion(getEnclosingClassName());
		else
			return ConstantMembers.callOrigStatic;
	}

	/**
	 * Prepares the class for implicit team activation by adding appropriate calls to
	 * _OT$implicitlyActivate(), _OT$implicitlyDeactivate() into all relevant methods.
	 */
	@Override
	protected void prepareTeamActivation() {
		if (!isFirstTransformation || isInterface())
			return;
		if (isTeam() || isRole())
			multiAdapter.addVisitor(new AddImplicitActivationAdapter(this.writer, this));
		AddGlobalTeamActivationAdapter.checkAddVisitor(this.multiAdapter, this.writer);
	}

	@Override
	protected void prepareLiftingParticipant() {
		if (isTeam() && LiftingParticipantAdapter.isLiftingParticipantConfigured(this.loader)) {
			multiAdapter.addVisitor(new LiftingParticipantAdapter(this.writer));
		}
	}

	/**
	 * Prepares the methods callAllBindings and callOrig with an empty
	 * switch statement
	 */
	@Override
	protected void prepareForFirstTransformation() {
		if (!isTransformed && !isInterface()) {
			String internalSuperClassName = this.weavingContext.isWeavable(getSuperClassName()) ? getInternalSuperClassName() : null;
			nodes.add(new CreateSwitchAdapter(ConstantMembers.callOrig, internalSuperClassName));
			nodes.add(new CreateSwitchForCallAllBindingsNode());
			nodes.add(new CreateAddRemoveRoleMethod());
			isTransformed = true;
		}
	}

	/**
	 * Prepares the method callOrigStatic with an empty
	 * switch statement
	 */
	@Override
	protected void prepareForFirstStaticTransformation() {
		if (!isTransformedStatic && !isInterface()) {
			nodes.add(new CreateSwitchAdapter(getCallOrigStatic(), isRole()));
			isTransformedStatic = true;
		}
	}

	/**
	 * Prepares the methods access and accessStatic with an empty
	 * switch statement
	 */
	@Override
	protected void prepareForFirstMemberAccess() {
		if (!isTransformedForMemberAccess && !isInterface()) {
			String internalSuperClassName = this.weavingContext.isWeavable(getSuperClassName()) ? getInternalSuperClassName() : null;
			nodes.add(new CreateSwitchForAccessAdapter(ConstantMembers.access, internalSuperClassName, this));
			nodes.add(new CreateSwitchForAccessAdapter(ConstantMembers.accessStatic, internalSuperClassName, this));
			isTransformedForMemberAccess = true;
		}

	}

	/**
	 * Was the class already transformed?
	 */
	@Override
	public boolean isFirstTransformation() {
		return isFirstTransformation;
	}
}
