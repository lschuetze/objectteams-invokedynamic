/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2014 Oliver Frank and others.
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.objectteams.otredyn.bytecode.AbstractBoundClass;
import org.eclipse.objectteams.otredyn.bytecode.Binding;
import org.eclipse.objectteams.otredyn.bytecode.ClassRepository;
import org.eclipse.objectteams.otredyn.runtime.ClassIdentifierProviderFactory;
import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.IClassIdentifierProvider;
import org.eclipse.objectteams.otredyn.runtime.IBinding.CallinModifier;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;

/**
 * This class contains all classes representing OT/J class file attributes
 * @author Oliver Frank
 */
public abstract class Attributes {
	protected final static String ATTRIBUTE_OT_DYN_CALLIN_BINDINGS="OTDynCallinBindings";
	protected final static String ATTRIBUTE_ROLE_BASE_BINDINGS = "CallinRoleBaseBindings";
	protected final static String ATTRIBUTE_CALLIN_PRECEDENCE = "CallinPrecedence";
	protected final static String ATTRIBUTE_OT_CLASS_FLAGS = "OTClassFlags";
	protected final static String ATTRIBUTE_OT_SPECIAL_ACCESS = "OTSpecialAccess";
	public    final static String ATTRIBUTE_OT_COMPILER_VERSION = "OTCompilerVersion";
	
	public static final int OTDRE_FLAG = 0x8000; // high bit in OTCompilerVersion

	protected final static Attribute[] attributes = { 
		new CallinBindingsAttribute(0),
		new RoleBaseBindingsAttribute(0),
		new CallinPrecedenceAttribute(0),
		new OTClassFlagsAttribute(0),
		new OTSpecialAccessAttribute(),
		new OTCompilerVersion(0)
	};
	protected static class OTCompilerVersion extends Attribute {
		private int version;		
		protected OTCompilerVersion(int version) {
			super(ATTRIBUTE_OT_COMPILER_VERSION);
			this.version = version;
		}
		@Override
		protected Attribute read(ClassReader cr, int off, int len, char[] buf, int codeOff, Label[] labels) {
			int encodedVersion  = cr.readUnsignedShort(off);
			if ((encodedVersion & OTDRE_FLAG) == 0)
            	throw new UnsupportedClassVersionError("OTDRE: Class "+cr.getClassName()+" was compiled for incompatible weaving target OTRE");
			return new OTCompilerVersion(encodedVersion);
		}
		@Override public String toString() {
			return this.type+' '+this.version;
		}
	}
	
	protected static class CallinBindingsAttribute extends Attribute {
		static final short COVARIANT_BASE_RETURN = 8;
		static final short BASE_SUPER_CALL = 16;

		/** Represents all base method bindings of one callin binding. */
		protected static class MultiBinding {
			private String   roleClassName;
			private String roleMethodName;
			private String roleMethodSignature;
			private String   callinLabel;
			private String   baseClassName;
			private String[] baseMethodNames;
			private String[] baseMethodSignatures;
			private String[] declaringBaseClassNames;
			private CallinModifier      callinModifier;
			private int[]    callinIds;
			private int[]    baseFlags;
			private boolean  isHandleCovariantReturn;
			private boolean  requireBaseSuperCall;
			
			MultiBinding(String roleName, String roleMethodName, String roleMethodSignature,
					String callinLabel, String baseClassName, 
						 String[] baseMethodNames, String[] baseMethodSignatures, String[] declaringBaseClassNames,
						 CallinModifier callinModifier, int[] callinIds, int[] baseFlags, int flags) 
			{
				this.roleClassName = roleName;
				this.roleMethodName = roleMethodName;
				this.roleMethodSignature = roleMethodSignature;
				this.callinLabel = callinLabel;
				this.baseClassName = baseClassName;
				this.baseMethodNames = baseMethodNames;
				this.baseMethodSignatures = baseMethodSignatures;
				this.declaringBaseClassNames = declaringBaseClassNames;
				this.callinModifier = callinModifier;
				this.callinIds = callinIds;
				this.baseFlags = baseFlags;
				this.isHandleCovariantReturn = (flags & COVARIANT_BASE_RETURN) != 0;
				this.requireBaseSuperCall = (flags & BASE_SUPER_CALL) != 0;
			}
			
			protected String getRoleClassName() {
				return roleClassName;
			}
			
			protected String getRoleMethodName() {
				return roleMethodName;
			}
			
			protected String getRoleMethodSignature() {
				return roleMethodSignature;
			}

			protected String getBaseClassName() {
				return baseClassName;
			}

			protected String[] getBaseMethodNames() {
				return baseMethodNames;
			}

			protected String[] getBaseMethodSignatures() {
				return baseMethodSignatures;
			}
			
			protected CallinModifier getCallinModifier() {
				return this.callinModifier;
			}
			
			protected int[] getCallinIds() {
				return callinIds;
			}

			public int[] getBaseFlags() {
				return baseFlags;
			}

			protected String getCallinLabel() {
				return callinLabel;
			}
			public boolean isHandleCovariantReturn() {
				return this. isHandleCovariantReturn;
			}
			public boolean requiresBaseSuperCall() {
				return this.requireBaseSuperCall;
			}
			public String[] getDeclaringBaseClassName() {
				return this.declaringBaseClassNames;
			}
		}
		
		private MultiBinding[] bindings;

		public CallinBindingsAttribute(int bindingsCount) {
			super(ATTRIBUTE_OT_DYN_CALLIN_BINDINGS);
			this.bindings = new MultiBinding[bindingsCount];
		}
		
		private void addBinding(int i, String roleName, String roleMethodName, String roleMethodSignature, 
				String callinLabel, String baseClassName, 
				                String[] baseMethodNames, String[] baseMethodSignatures, String[] declaringBaseClassNames,
				                String callinModifierName, int[] callinIds, int[] baseFlags, int flags) {
			CallinModifier callinModifier = CallinModifier.fromString(callinModifierName);
			this.bindings[i] = new MultiBinding(roleName, roleMethodName, roleMethodSignature,
					callinLabel, baseClassName, 
					                            baseMethodNames, baseMethodSignatures, declaringBaseClassNames,
					                            callinModifier, callinIds, baseFlags, flags);
		}
		
		@Override
		protected Attribute read(ClassReader cr, int off, int len,
				char[] buf, int codeOff, Label[] labels) 
		{
			int bindingsCount = cr.readShort(off);							off += 2;
			CallinBindingsAttribute attr = new CallinBindingsAttribute(bindingsCount);
			for (int i = 0; i < bindingsCount; i++) {
				String roleName					= cr.readUTF8(off, buf);	off += 2;
				String callinLabel				= cr.readUTF8(off, buf);	off += 2;
				String roleMethodName = cr.readUTF8(off, buf);	off += 2;
				String roleMethodSignature = cr.readUTF8(off, buf);	off += 2;
				///* skip roleSelector, roleSignature */						off += 4;
				String callinModifier			= cr.readUTF8(off, buf);	off += 2;
				int flags						= cr.readByte(off);			off += 1;
				String baseClassName 			= cr.readUTF8(off, buf);	off += 2;
				/* skip filename & lineNumber & lineOffset */				off += 6;
				int baseMethodsCount 			= cr.readShort(off);		off += 2;
				String[] baseMethodNames 		= new String[baseMethodsCount];
				String[] baseMethodSignatures 	= new String[baseMethodsCount];
				String[] declaringBaseClassNames 	= new String[baseMethodsCount];
				int[] callinIds = new int[baseMethodsCount];
				int[] baseFlags = new int[baseMethodsCount];
				for (int m = 0; m < baseMethodsCount; m++) {
					baseMethodNames[m] 			= cr.readUTF8(off, buf);	off += 2;
					baseMethodSignatures[m]		= cr.readUTF8(off, buf);	off += 2;
					declaringBaseClassNames[m]  = cr.readUTF8(off, buf);	off += 2;
					callinIds[m] 				= cr.readInt(off);			off += 4;
					baseFlags[m]				= cr.readByte(off);			off++;
					/* skip translationFlags */								off += 2;
				}
				attr.addBinding(i, roleName, roleMethodName, roleMethodSignature,
						callinLabel,	 baseClassName,
								baseMethodNames, baseMethodSignatures, declaringBaseClassNames,
								callinModifier, callinIds, baseFlags, flags);
			}
			return attr;
		}
		
		public MultiBinding[] getBindings() {
			return this.bindings;
		}
		
		@Override
		public String toString() {
			StringBuffer buf = new StringBuffer();
			for (MultiBinding binding : this.bindings) {
				buf.append(binding.getBaseClassName());
				int[] callinIds = binding.getCallinIds();
				String[] baseMethodNames = binding.getBaseMethodNames();
				String[] baseMethodSignatures = binding.getBaseMethodSignatures();
				for (int i=0; i<callinIds.length; i++) {
					buf.append("\n\t{");
					buf.append(callinIds[i]);
					buf.append("} ");
					buf.append(baseMethodNames[i]);
					buf.append(baseMethodSignatures[i]);
				}				
			}
			return buf.toString();
		}
	}
	
	protected static class RoleBaseBindingsAttribute extends Attribute {
		String[] roles;
		String[] bases;
		protected RoleBaseBindingsAttribute(int elementCount) {
			super(ATTRIBUTE_ROLE_BASE_BINDINGS);
			roles = new String[elementCount];
			bases = new String[elementCount];
		}
		@Override
		protected Attribute read(ClassReader cr, int off, int len,
				char[] buf, int codeOff, Label[] labels) 
		{
			int elementCount = cr.readShort(off);		off += 2;
			RoleBaseBindingsAttribute attr = new RoleBaseBindingsAttribute(elementCount);
			for (int i = 0; i < elementCount; i++) {
				attr.roles[i] = cr.readUTF8(off, buf);	off += 2;
				attr.bases[i] = cr.readUTF8(off, buf);	off += 2;
			}
			return attr;
		}
		@Override
		public String toString() {
			StringBuilder buf = new StringBuilder(this.type).append('\n');
			for (int i = 0; i < roles.length; i++) {
				buf.append('\t').append(roles[i]).append("->").append(bases[i]).append('\n');
			}
			return buf.toString();
		}
	}
	protected static class CallinPrecedenceAttribute extends Attribute {
		String[] labels;
		public CallinPrecedenceAttribute(int elementCount) {
			super(ATTRIBUTE_CALLIN_PRECEDENCE);
			this.labels = new String[elementCount];
		}
		@Override
		protected Attribute read(ClassReader cr, int off, int len,
				char[] buf, int codeOff, Label[] labels) 
		{
			int elementCount = cr.readShort(off);		off += 2;
			CallinPrecedenceAttribute attr = new CallinPrecedenceAttribute(elementCount);
			for (int i = 0; i < elementCount; i++) {
				attr.labels[i] = cr.readUTF8(off, buf);	off += 2;
			}
			return attr;
		}
	}
	protected static class OTClassFlagsAttribute extends Attribute {
		int flags;
		protected OTClassFlagsAttribute(int flags) {
			super(ATTRIBUTE_OT_CLASS_FLAGS);
			this.flags = flags;
		}
		@Override
		protected Attribute read(ClassReader cr, int off, int len, char[] buf,
				int codeOff, Label[] labels) 
		{
			return new OTClassFlagsAttribute(cr.readUnsignedShort(off));			
		}
	}
	protected static class OTSpecialAccessAttribute extends Attribute {
		class DecapsField {
			String accessMode;
			boolean isStatic;
			String baseclass, name, desc;
			public int perTeamAccessId;
			public DecapsField(String baseclass, String name, String desc, int accessId, String accessMode, boolean isStatic) {
				this.baseclass = baseclass;
				this.name = name;
				this.desc = desc;
				this.perTeamAccessId = accessId;
				this.accessMode = accessMode;
				this.isStatic = isStatic;
			}
		}
		class DecapsMethod {
			String[] weaveIntoClasses;
			String declaringClass, name, desc;
			int perTeamAccessId;
			boolean isStatic;
			DecapsMethod(String weaveIntoClasses, String declaringClass, String name, String desc, int id, boolean isStatic) {
				this.weaveIntoClasses = weaveIntoClasses.split(":");
				this.declaringClass = declaringClass;
				this.name = name;
				this.desc = desc;
				this.perTeamAccessId = id;
				this.isStatic = isStatic;
			}
		}
		private static final int DECAPSULATION_METHOD_ACCESS= 4; // kinds are disjoint from those used by the old OTRE
		private static final int CALLOUT_FIELD_ACCESS = 5;

		List<DecapsMethod> methods = new ArrayList<DecapsMethod>();
		List<DecapsField> fields = new ArrayList<DecapsField>();
		List<String> decapsulatedBaseClasses = new ArrayList<String>(); // not currently used, see AddInterfaceAdapter for brute force solution
		
		protected OTSpecialAccessAttribute() {
			super(ATTRIBUTE_OT_SPECIAL_ACCESS);
		}
		@Override
		protected Attribute read(ClassReader cr, int off, int len, char[] buf,
				int codeOff, Label[] labels) 
		{
			OTSpecialAccessAttribute attr = new OTSpecialAccessAttribute();
			int size = cr.readUnsignedShort(off); 			off+=2;
			for (int i=0; i<size; i++) {
				int kind = cr.readByte(off++);
				switch (kind) {
				case DECAPSULATION_METHOD_ACCESS:
					attr.readMethodAccess(cr, off, buf);	off+=8;
					break;
				case CALLOUT_FIELD_ACCESS:
					attr.readFieldAccess(cr, off, buf); 	off+=9;
					break;
				default:
					throw new IllegalStateException("Unexpected kind in OTSpecialAccess attribute: "+kind);
				}
			}
			size = cr.readUnsignedShort(off);				off+=2;
			for (int i = 0; i < size; i++) {
				String baseClass = cr.readUTF8(off, buf);	off+=2;
				int flag = cr.readByte(off++);
				if (flag == 1)
					decapsulatedBaseClasses.add(baseClass);
			}
			return attr;
		}
		private void readMethodAccess(ClassReader cr, int off, char[] buf) {
			String className   = cr.readUTF8(off, buf);
			String encodedName = cr.readUTF8(off+2, buf);
			String methodDesc  = cr.readUTF8(off+4, buf);
			int accessId = cr.readUnsignedShort(off+6);
			boolean isStatic = false;
			String declaringClass;
			String methodName;
			if (encodedName.charAt(0) == '<') {
				// constructor
				declaringClass = className;
				methodName = encodedName;
				isStatic = true; // use static accessor
			} else {
				int pos = encodedName.indexOf('?');
				if (pos == -1) {
					pos = encodedName.indexOf('!');
					isStatic = true;
				}
				declaringClass = encodedName.substring(0, pos);
				methodName = encodedName.substring(pos+1);
			}
			this.methods.add(new DecapsMethod(className, declaringClass, methodName, methodDesc, accessId, isStatic));
		}
		private void readFieldAccess(ClassReader cr, int off, char[] buf) {
			int accessId = cr.readUnsignedShort(off);
			int flags = cr.readByte(off+2);
			String className   = cr.readUTF8(off+3, buf);
			String fieldName = cr.readUTF8(off+5, buf);
			String fieldDesc  = cr.readUTF8(off+7, buf);
			boolean isStatic = (flags & 2) != 0;
			String accessMode = (flags & 1) == 1 ? "set" : "get";
			this.fields.add(new DecapsField(className, fieldName, fieldDesc, accessId, accessMode, isStatic));
		}
		public void registerAt(AsmBoundClass clazz) {
			ClassRepository repo = ClassRepository.getInstance();
			IClassIdentifierProvider provider = ClassIdentifierProviderFactory.getClassIdentifierProvider();

			for (DecapsMethod dMethod : this.methods) {
				// FIXME(SH): the following may need adaptation for OT/Equinox or other multi-classloader settings:
				// bypassing the identifier provider (we don't have a Class<?> yet):
				// String boundClassIdentifier = provider.getBoundClassIdentifier(clazz, dMethod.baseclass);
				AbstractBoundClass baseclass = repo.getBoundClass(dMethod.declaringClass, dMethod.declaringClass.replace('.', '/'), clazz.getClassLoader());
				// register the target method:
				baseclass.getMethod(dMethod.name, dMethod.desc, false/*covariantReturn*/, dMethod.isStatic);
				clazz.recordAccessId(dMethod.perTeamAccessId);
				clazz.addBinding(new Binding(clazz, dMethod.declaringClass, dMethod.name, dMethod.desc, dMethod.perTeamAccessId, IBinding.BindingType.METHOD_ACCESS));
			}

			for (DecapsField dField: this.fields) {
				// FIXME(SH): the following may need adaptation for OT/Equinox or other multi-classloader settings:
				// bypassing the identifier provider (we don't have a Class<?> yet):
				// String boundClassIdentifier = provider.getBoundClassIdentifier(clazz, dMethod.baseclass);
				AbstractBoundClass baseclass = repo.getBoundClass(dField.baseclass, dField.baseclass.replace('.', '/'), clazz.getClassLoader());
				// register the target field:
				baseclass.getField(dField.name, dField.desc);
				clazz.recordAccessId(dField.perTeamAccessId);
				clazz.addBinding(new Binding(clazz, dField.baseclass, dField.name, dField.desc, dField.perTeamAccessId, IBinding.BindingType.FIELD_ACCESS));
			}
		}
	}
}
