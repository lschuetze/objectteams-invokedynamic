/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2015 Oliver Frank and others.
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

import java.util.List;
import java.util.ListIterator;

import org.eclipse.objectteams.otredyn.bytecode.AbstractBoundClass;
import org.eclipse.objectteams.otredyn.bytecode.Method;
import org.eclipse.objectteams.otredyn.transformer.names.ClassNames;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.eclipse.objectteams.otredyn.bytecode.asm.AsmBoundClass.ASM_API;
import static org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers.callOrig;

/**
 * Every class, that wants to manipulate the bytecode of a class
 * with the ASM Tree API, have to inherit from this class and do
 * the transformations in the method transform().
 * Additionally the class provides util methods to
 * manipulate the bytecode 
 * @author Oliver Frank
 */
public abstract class AbstractTransformableClassNode extends ClassNode {
	
	static final boolean IS_DEBUG = System.getProperty("ot.debug") != null;

	public AbstractTransformableClassNode() {
		super(ASM_API);
	}

	/**
	 * Returns instructions, that are needed to pack all arguments of a method
	 * in an {@link Object} Array
	 * @param args The Types of the arguments
	 * @param isStatic is this method static or not
	 * @return
	 */
	protected InsnList getBoxingInstructions(Type[] args, boolean isStatic) {
		int firstArgIndex = 1;
		if (isStatic) {
			firstArgIndex = 0;
		}
		InsnList instructions = new InsnList();
		instructions.add(createLoadIntConstant(args.length));
		instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY,
				ClassNames.OBJECT_SLASH));
		for (int i=0, slot=0; i < args.length; slot += args[i++].getSize()) {
			instructions.add(new InsnNode(Opcodes.DUP));
			instructions.add(createLoadIntConstant(i));
			instructions.add(new IntInsnNode(args[i].getOpcode(Opcodes.ILOAD),
					slot + firstArgIndex));
			if (args[i].getSort() != Type.OBJECT
					&& args[i].getSort() != Type.ARRAY) {
				instructions.add(AsmTypeHelper
						.getBoxingInstructionForType(args[i]));
			}
			instructions.add(new InsnNode(Opcodes.AASTORE));
		}

		return instructions;
	}

	/**
	 * Returns the instructions, that are needed to convert 
	 * a return value of the type {@link Object} to the real type
	 * @param returnType the real type
	 * @return
	 */
	protected InsnList getUnboxingInstructionsForReturnValue(Type returnType) {
		InsnList instructions = new InsnList();
		switch (returnType.getSort()) {
		case Type.VOID:
			instructions.add(new InsnNode(Opcodes.POP));
			instructions.add(new InsnNode(Opcodes.RETURN));
			break;
		case Type.ARRAY: // fallthrough
		case Type.OBJECT:
			instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType
					.getInternalName()));
			instructions.add(new InsnNode(Opcodes.ARETURN));
			break;
		default:
			String objectType = AsmTypeHelper.getBoxingType(returnType);
			instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, objectType));
			instructions.add(AsmTypeHelper.getUnboxingInstructionForType(
					returnType, objectType));
			instructions
					.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
		}
		return instructions;
	}

	/**
	 * Adds a new Label to an existing switch statement
	 * @param instructions the instructions, in which the switch statement is defined
	 * @param newInstructions the instructions of the new label
	 * @param labelIndex the index of the label
	 */
	protected void addNewLabelToSwitch(InsnList instructions,
			InsnList newInstructions, int labelIndex) {
		ListIterator<AbstractInsnNode> iter = instructions.iterator();
		LookupSwitchInsnNode lSwitch = null;
		while (iter.hasNext()) {
			AbstractInsnNode node = (AbstractInsnNode) iter.next();
			if (node.getType() == AbstractInsnNode.LOOKUPSWITCH_INSN) {
				lSwitch = (LookupSwitchInsnNode) node;
				LabelNode label = new LabelNode();
				boolean labelAdded = false;
				for (int i = 0; i < lSwitch.keys.size(); i++) {
					Integer key = (Integer) lSwitch.keys.get(i);
					if (key >= labelIndex) {
						lSwitch.keys.add(i, labelIndex);
						lSwitch.labels.add(i, label);
						labelAdded = true;
						break;
					}
				}
				if (!labelAdded) {
					lSwitch.labels.add(label);
					lSwitch.keys.add(labelIndex);
				}
				boolean foundDefLabel = false;
				AbstractInsnNode prevNode = node;
				while (iter.hasNext()) {
					node = (AbstractInsnNode) iter.next();
					if (node.getType() == AbstractInsnNode.LABEL) {
						if (!foundDefLabel) {
							foundDefLabel = true;
						} else {
							break;
						}
					}
					prevNode = node;
				}
				instructions.insert(prevNode, label);
				instructions.insert(label, newInstructions);
				break;
			}	
		}
		if (lSwitch == null) {
			throw new RuntimeException("No switch statement found.");
		}
	}

	/**
	 * Returns a {@link MethodNode} for a given {@link Method} instance
	 * @param method
	 * @return the {@link MethodNode} or null if there is no such method
	 */
	protected MethodNode getMethod(Method method) {
		List<MethodNode> methodList = methods;
		for (MethodNode methodNode : methodList) {
			if (methodNode.name.compareTo(method.getName()) == 0
					&& methodNode.desc.compareTo(method.getSignature()) == 0) {
				return methodNode;
			}
		}

		return null;
	}

	/**
	 * This method could be used to generate debug outputs in the generated code in the form: <br>
	 * <code>
	 * Sytsem.out.println(message);
	 * </code>
	 * @param message
	 * @return
	 */
	protected InsnList getInstructionsForDebugOutput(String message) {
		InsnList instructions = new InsnList();
		instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
				"java/lang/System", "out", "Ljava/io/PrintStream;"));
		instructions.add(new LdcInsnNode(message));
		instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
				"java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
		return instructions;
	}

	protected InsnList getInstructionsForDebugObjectOutput(AbstractInsnNode getObjectInsn) {
		InsnList instructions = new InsnList();
		instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
				"java/lang/System", "out", "Ljava/io/PrintStream;"));
		instructions.add(getObjectInsn);
		instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
				"java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false));
		return instructions;
	}

	protected InsnList getInstructionsForDebugIntOutput(AbstractInsnNode getIntInsn) {
		InsnList instructions = new InsnList();
		instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
				"java/lang/System", "out", "Ljava/io/PrintStream;"));
		instructions.add(getIntInsn);
		instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
				"java/io/PrintStream", "println", "(I)V", false));
		return instructions;
	}

	/**
	 * Adds instructions to put all arguments of a method on the stack.
	 * @param instructions
	 * @param args
	 * @param isStatic
	 */
	protected void addInstructionsForLoadArguments(InsnList instructions, Type[] args, boolean isStatic) {
		int firstArgIndex = 1;
		if (isStatic) {
			firstArgIndex = 0;
		}
		// put "this" on the stack for an non-static method
		if (!isStatic) {
			instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		}
		for (int i=0, slot=firstArgIndex; i < args.length; slot+=args[i++].getSize()) {
			instructions.add(new IntInsnNode(args[i].getOpcode(Opcodes.ILOAD),
					slot));
		}
	}
	
	/**
	 * Replace all return statements in the given instructions with new 
	 * statements that convert the real return value to {@link Object}
	 * and return this new {@link Object}
	 * 
	 * @param instructions
	 * @param returnType
	 */
	protected void replaceReturn(InsnList instructions, Type returnType) {
		if (returnType.getSort() != Type.OBJECT &&
				returnType.getSort() != Type.ARRAY &&
				returnType.getSort() != Type.VOID) {
			ListIterator<AbstractInsnNode> orgMethodIter = instructions.iterator();
			while (orgMethodIter.hasNext()) {
				AbstractInsnNode orgMethodNode = orgMethodIter.next();
				if (orgMethodNode.getOpcode() == returnType.getOpcode(Opcodes.IRETURN)) {
					instructions.insertBefore(orgMethodNode, AsmTypeHelper.getBoxingInstructionForType(returnType));
					instructions.set(orgMethodNode, new InsnNode(Opcodes.ARETURN));
				}
			}
		} else if (returnType.getSort() == Type.VOID) {
			ListIterator<AbstractInsnNode> orgMethodIter = instructions.iterator();
			while (orgMethodIter.hasNext()) {
				AbstractInsnNode orgMethodNode = orgMethodIter.next();
				if (orgMethodNode.getOpcode() == Opcodes.RETURN) {
					instructions.insertBefore(orgMethodNode, new InsnNode(Opcodes.ACONST_NULL));
					instructions.insertBefore(orgMethodNode, new InsnNode(Opcodes.ARETURN));
					instructions.remove(orgMethodNode);
				}
			}
		}
	}

	protected void addReturn(InsnList instructions, Type returnType) {
		switch (returnType.getSort()) {
		case Type.VOID:
			instructions.add(new InsnNode(Opcodes.RETURN));
			break;
		case Type.OBJECT:
		case Type.ARRAY:
			instructions.add(new InsnNode(Opcodes.ACONST_NULL));
			instructions.add(new InsnNode(Opcodes.ARETURN));
			break;
		case Type.INT:
		case Type.BOOLEAN:
		case Type.BYTE:
		case Type.CHAR:
		case Type.SHORT:
			instructions.add(new InsnNode(Opcodes.ICONST_0));
			instructions.add(new InsnNode(Opcodes.IRETURN));
			break;
		case Type.LONG:
			instructions.add(new InsnNode(Opcodes.LCONST_0));
			instructions.add(new InsnNode(Opcodes.LRETURN));
			break;
		case Type.FLOAT:
			instructions.add(new InsnNode(Opcodes.FCONST_0));
			instructions.add(new InsnNode(Opcodes.FRETURN));
			break;
		case Type.DOUBLE:
			instructions.add(new InsnNode(Opcodes.DCONST_0));
			instructions.add(new InsnNode(Opcodes.DRETURN));
			break;
		default:
			throw new IllegalArgumentException("Unexpected type "+returnType);
		}
	}

	/**
	 * Create an instruction for loading an integer constant,
	 * using the most compact possible format. 
	 */
	protected AbstractInsnNode createLoadIntConstant(int constant) {
		if (constant >= 0 && constant <= 5)
			return new InsnNode(Opcodes.ICONST_0+constant);
		else if (constant > Byte.MIN_VALUE && constant < Byte.MAX_VALUE)
			return new IntInsnNode(Opcodes.BIPUSH, constant);
		else if (constant > Short.MIN_VALUE && constant < Short.MAX_VALUE)
			return new IntInsnNode(Opcodes.SIPUSH, constant);		
		else
			return new LdcInsnNode(constant);
	}
	
	/** Call back interface for {@link #replaceSuperCallsWithCallToCallOrig()}. */
	protected interface IBoundMethodIdInsnProvider {
		AbstractInsnNode getLoadBoundMethodIdInsn(MethodInsnNode methodInsn);
	}

	protected void replaceSuperCallsWithCallToCallOrig(InsnList instructions, List<MethodInsnNode> superCalls, 
			boolean returnsJLObject, AbstractBoundClass superclass, IBoundMethodIdInsnProvider insnProvider) {
		for (MethodInsnNode oldNode : superCalls) {
			
			superclass.addWeavingOfSubclassTask(oldNode.name, oldNode.desc, oldNode.getOpcode() == Opcodes.INVOKESTATIC);
			
			Type[] args = Type.getArgumentTypes(oldNode.desc);
			Type returnType = Type.getReturnType(oldNode.desc);
	
			// we need to insert into the loading sequence before the invocation, find the insertion points:
			AbstractInsnNode[] insertionPoints = StackBalanceAnalyzer.findInsertionPointsBefore(oldNode, args);
			AbstractInsnNode firstInsert = insertionPoints.length > 0 ? insertionPoints[0] : oldNode;
			
			// push first arg to _OT$callOrig():
			instructions.insertBefore(firstInsert, insnProvider.getLoadBoundMethodIdInsn(oldNode));
			
			// prepare array as second arg to _OT$callOrig():
			instructions.insertBefore(firstInsert, createLoadIntConstant(args.length));
			instructions.insertBefore(firstInsert, new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
			
			for (int i = 0; i < insertionPoints.length; i++) {
				// NB: each iteration has an even stack balance, where the top is the Object[].
				instructions.insertBefore(insertionPoints[i], new InsnNode(Opcodes.DUP));
				instructions.insertBefore(insertionPoints[i], createLoadIntConstant(i));
				// leave the original loading sequence in tact and continue at the next point:
				AbstractInsnNode insertAt = (i +1 < insertionPoints.length) ? insertionPoints[i+1] : oldNode;
				instructions.insertBefore(insertAt, AsmTypeHelper.getBoxingInstructionForType(args[i]));
				instructions.insertBefore(insertAt, new InsnNode(Opcodes.AASTORE));
			}
	
			// before an areturn j.l.Object we don't need any type adjustments
			// (incl. the case where we change another return to areturn j.l.O):
			boolean nextIsGeneralizedReturn = false;
			AbstractInsnNode next = oldNode.getNext();
			if (returnsJLObject)
				nextIsGeneralizedReturn = next != null && next.getOpcode() >= Opcodes.IRETURN && next.getOpcode() <= Opcodes.ARETURN; 

			if (!nextIsGeneralizedReturn) {
				if (returnType == Type.VOID_TYPE) {
					instructions.insert(oldNode, new InsnNode(Opcodes.POP));
				} else {
					instructions.insert(oldNode, AsmTypeHelper.getUnboxingInstructionForType(returnType));
					String expectedReferenceTypeName = AsmTypeHelper.getBoxingType(returnType);
					if (expectedReferenceTypeName == null)
						expectedReferenceTypeName = returnType.getInternalName();
					instructions.insert(oldNode, new TypeInsnNode(Opcodes.CHECKCAST, expectedReferenceTypeName)); // before unboxing, if any
				}
			}
	
			MethodInsnNode newMethodNode = new MethodInsnNode(Opcodes.INVOKESPECIAL, ((MethodInsnNode)oldNode).owner, callOrig.getName(), callOrig.getSignature(), false);
			instructions.set(oldNode, newMethodNode);
			if (nextIsGeneralizedReturn && next != null && next.getOpcode() != Opcodes.ARETURN)
				instructions.set(next, new InsnNode(Opcodes.ARETURN)); // prevent further manipulation by replaceReturn()
		}
	}

	/**
	 * Add a local variable to be visible throughout the all of the instruction list.
	 * Side effect: may add labels at beginning and end, unless labels are already present at these locations.
	 */
	protected void addLocal(MethodNode method, String selector, String desc, int slot) {
		if (!IS_DEBUG) return;
		InsnList instructions = method.instructions;
		LabelNode start, end;
		if (instructions.getFirst() instanceof LabelNode) {
			start = (LabelNode) instructions.getFirst();
		} else {
			start = new LabelNode();
			instructions.insert(start);
		}
		if (instructions.getLast() instanceof LabelNode) {
			end = (LabelNode) instructions.getLast();
		} else {
			end = new LabelNode();
			instructions.add(end);
		}
		addLocal(method, selector, desc, slot, start, end, true);
	}

	/**
	 * Add a local variable to be visible from 'start' to 'end'.
	 * Checks whether a local variable of that name already exists, in which case we don't change anything.
	 * TODO: should check if ranges of both variables overlap!
	 * @param fullRange TODO
	 */
	protected void addLocal(MethodNode method, String selector, String desc, int slot, LabelNode start, LabelNode end, boolean fullRange) {
		if (!IS_DEBUG) return;
		if (fullRange) {
			for (Object lv : method.localVariables) {
				if (((LocalVariableNode)lv).name.equals(selector)) {
					return;
				}
			}
		}
        method.localVariables.add(new LocalVariableNode(selector, desc, null, start, end, slot));
	}

	protected void addThisVariable(MethodNode method) {
		addLocal(method, "this", "L"+this.name+";", 0);
	}

	protected void addLineNumber(InsnList instructions, int line) {
		if (!IS_DEBUG) return;
		LabelNode position = new LabelNode();
		instructions.add(position);
		instructions.add(new LineNumberNode(line, position));
	}

	protected int peekFirstLineNumber(InsnList instructions) {
		if (!IS_DEBUG) return -1;
		ListIterator<AbstractInsnNode> iterator = instructions.iterator();
		while (iterator.hasNext()) {
			Object insn = iterator.next();
			if (insn instanceof LineNumberNode)
				return ((LineNumberNode) insn).line;
		}
		return -1;
	}
	/**
	 * In this method, concrete Implementations of this class
	 * can manipulate the bytecode
	 * @return whether transformation actually happened
	 */
	protected abstract boolean transform();
}
