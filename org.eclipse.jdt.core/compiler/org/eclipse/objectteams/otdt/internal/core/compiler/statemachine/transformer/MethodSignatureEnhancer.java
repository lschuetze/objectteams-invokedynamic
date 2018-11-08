/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2006 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: MethodSignatureEnhancer.java 23417 2010-02-03 20:13:55Z stephan $
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions.WeavingScheme;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.objectteams.otdt.core.compiler.IOTConstants;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.MethodModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstGenerator;

/**
 * This class takes over the responsibility of the BaseCallRedirection Transformer of the OTRE
 * wrt. signature enhancing.
 * For this task it closely collaborates with BaseCallMessageSend, CallinImplementor and BaseReference.
 * In conjunction BaseCallMessageSend and TransformStatementsVisitor take care of replacing base calls
 * by calls to the base call surrogate.
 * The changes mainly involve the following steps:
 * <ul>
 * <li> Parser initially adds enhanced args to callin methods
 * <li> Resolving MethodSpecs is now aware of its position (expectingCallin)
 * <li> MethodSpecs for callin methods maintain two signatures:
 *      getSourceParameters() and the enhanced resolvedMethod.parameters()
 * <li> MethodModel stores the original return type of callin methods
 *		(before generalizing all base types (including void) to Object)
 * <li> BaseCallMessageSend is now responsible for calls to the surrogate
 * <li> BaseCallMessageSend can additionally handle base calls within
 *		local classes within a callin method.
 * 		(The latter feature also concerns BaseReference)
 * <li>	generate MethodBindings representing future base call surrogates
 *		(both SourceTypeBinding and BinaryTypeBinding)
 * <li> Link a callin MethodBinding and a base call surrogate via MethodModel
 *		and restore this structure also for copy inherited methods
 * <li> CallinMethodMappings still uses source signatures and stores
 * 		additional flag "staticRoleMethod"
 * <li> CallinImplementor has to carefully choose the correct signatures/
 *		return types.
 * <li> TransformStatementsVisitor has to enhance signatures of super and
 *		tsuper calls, as well as modify return statements in case the
 *		return type is adjusted (perhaps using boxing).
 *</ul>
 *
 * @author stephan
 *
 */
@SuppressWarnings("nls")
public class MethodSignatureEnhancer implements IOTConstants, TypeConstants, ClassFileConstants
{
	
	private final static char[] OBJECT_SIGNATURE = "Ljava/lang/Object;".toCharArray();

	/**
	 * Names for arguments in enhanced signature used for passing runtime meta-information to the wrapper/role method.
	 */
	private final char[][] ENHANCING_ARG_NAMES;


	/** Length of the sublist of enhancing arguments. */
	public final int ENHANCING_ARG_LEN;
	public final char[] UNUSED_ARGS = new char[0];
	
	public static MethodSignatureEnhancer[] variants = new MethodSignatureEnhancer[WeavingScheme.values().length];
	
	private WeavingScheme weavingScheme;

// {OT/JamVM support:
	private static final boolean JAMVM_ASPECTBI = System.getProperty("ot.jamvm.aspectBI") != null;
// CH}
//{OTDyn: make constants configurable:
	static {
		for(WeavingScheme scheme : WeavingScheme.values())
			variants[scheme.ordinal()] = new MethodSignatureEnhancer(scheme);
	}
	private MethodSignatureEnhancer(WeavingScheme weavingScheme) {
		this.weavingScheme = weavingScheme;
		if (weavingScheme == WeavingScheme.OTDRE) {
			this.ENHANCING_ARG_NAMES = new char[0][0]; // {
				// invdyn args begin
//					"lookup".toCharArray(),
//					"name".toCharArray(),
//					"type".toCharArray(),
				// invdyn args end
//					"_OT$baseArg".toCharArray(), 
//					"_OT$teams".toCharArray(), 
//					"_OT$index".toCharArray(),
//					"_OT$callinIds".toCharArray(),
//					"_OT$boundMethodId".toCharArray(),
//					"_OT$args".toCharArray() };
  // {OT/JamVM support:
		} else if (JAMVM_ASPECTBI) {
			this.ENHANCING_ARG_NAMES = new char[][] {
					"_OT$teamIterator".toCharArray(),
					"_OT$bindIdx".toCharArray(),
					"_OT$baseMethodTag".toCharArray(),
					"_OT$unusedArgs".toCharArray() };
  // CH}
		} else {
			this.ENHANCING_ARG_NAMES = new char[][] {
					"_OT$teams".toCharArray(),
					"_OT$teamIDs".toCharArray(),
					"_OT$idx".toCharArray(),
					"_OT$bindIdx".toCharArray(),
					"_OT$baseMethodTag".toCharArray(),
					"_OT$unusedArgs".toCharArray() };
		}
		this.ENHANCING_ARG_LEN = this.ENHANCING_ARG_NAMES.length;
//		this.UNUSED_ARGS = this.ENHANCING_ARG_NAMES[this.ENHANCING_ARG_LEN-1];
	}
// SH}
	
	/** Get Typebindings for all enhancing arguments. */
	private static TypeBinding[] getEnhancingArgTypes (Scope scope) {
//{OTDyn: configurable:
		if (scope.compilerOptions().weavingScheme == WeavingScheme.OTDRE)
			return new TypeBinding[] {
				// invdyn args start
//				scope.getType(IOTConstants.MH_LOOKUP, 5),
//				scope.getType(IOTConstants.STRING, 3),
//				scope.getType(IOTConstants.INVOKE_METHODTYPE, 4),
				// invdyn args end
//				scope.getType(IOTConstants.ORG_OBJECTTEAMS_IBOUNDBASE2, 3), 	// _OT$baseArg
//				scope.createArrayType(scope.getOrgObjectteamsITeam(), 1),  		// _OT$teams
//				TypeBinding.INT,    	                               	 		// _OT$index
//				scope.createArrayType(TypeBinding.INT, 1),         		 		// _OT$callinIds
//				TypeBinding.INT,   	                                	 		// _OT$boundMethodId 
//				scope.createArrayType(scope.getJavaLangObject(), 1), 	 		// _OT$args				
			};
// SH}
// {OT/JamVM support:
		else if (JAMVM_ASPECTBI)
			return new TypeBinding[] {
					scope.getJavaLangObject(),								 // _OT$teamIterator
					TypeBinding.INT,    	                               	 // _OT$bindIdx
					TypeBinding.INT,        	                           	 // _OT$baseMethodTag
					scope.createArrayType(scope.getJavaLangObject(), 1), 	 // _OT$unusedArgs
		};
// CH}
		return new TypeBinding[] {
					scope.createArrayType(scope.getOrgObjectteamsITeam(), 1),// _OT$teams
					scope.createArrayType(TypeBinding.INT, 1),         		 // _OT$teamIDs
					TypeBinding.INT,   	                                	 // _OT$idx
					TypeBinding.INT,    	                               	 // _OT$bindIdx
					TypeBinding.INT,        	                           	 // _OT$baseMethodTag
					scope.createArrayType(scope.getJavaLangObject(), 1), 	 // _OT$unusedArgs
		};
	}

	// ===== AST: =====

	/**
	 * Enhance the arguments of a callin method or its wrapper.
	 *
	 * @param arguments source arguments
	 * @param namePrefix prepend this to all names of enhancement arguments (needed for callin-to-callin)
	 * @param isWrapper are we enhancing a callin wrapper (vs. the actual callin method)?
	 * @param gen an AstGenerator positioned for generating new arguments (method.sourceEnd+1).
	 * @return a new array of args
	 */
	public static Argument[] enhanceArguments(
			Argument[] arguments, char[] namePrefix, boolean isWrapper, AstGenerator gen, WeavingScheme weavingScheme)
	{
		return variants[weavingScheme.ordinal()].internalEnhanceArguments(arguments, namePrefix, isWrapper, gen);
	}
	private Argument[] internalEnhanceArguments(
			Argument[] arguments, char[] namePrefix, boolean isWrapper, AstGenerator gen)
	{
		int fullLen = this.ENHANCING_ARG_LEN;
		if (arguments != null) fullLen += arguments.length; // source arguments?
		Argument[] newArgs = new Argument[fullLen];
		TypeReference[] enhanceTypes = 
//{OTDyn: configurable:
			this.weavingScheme == WeavingScheme.OTDRE
			? new TypeReference[] {
					// invdyn args begin
					gen.qualifiedTypeReference(MH_LOOKUP),
					gen.qualifiedTypeReference(STRING),
					gen.qualifiedTypeReference(INVOKE_METHODTYPE),
					//invdyn args end
					gen.qualifiedTypeReference(ORG_OBJECTTEAMS_IBOUNDBASE2),		// _OT$baseArg
					gen.qualifiedArrayTypeReference(ORG_OBJECTTEAMS_ITEAM, 1),	// _OT$teams
					gen.singleTypeReference(TypeConstants.INT),						// _OT$index
					new ArrayTypeReference(TypeConstants.INT, 1, gen.pos),			// _OT$callinIds
					gen.singleTypeReference(TypeConstants.INT),						// _OT$boundMethodId
					gen.qualifiedArrayTypeReference(JAVA_LANG_OBJECT, 1),			// _OT$args
				} 
// SH}
// {OT/JamVM support:
			: JAMVM_ASPECTBI
			? new TypeReference[] {
					gen.qualifiedTypeReference(JAVA_LANG_OBJECT),
					gen.singleTypeReference(TypeConstants.INT),
					gen.singleTypeReference(TypeConstants.INT),
					gen.qualifiedArrayTypeReference(JAVA_LANG_OBJECT, 1),
			}
// CH}
			: new TypeReference[] {
				gen.qualifiedArrayTypeReference(ORG_OBJECTTEAMS_ITEAM, 1),
				new ArrayTypeReference(TypeConstants.INT, 1, gen.pos),
				gen.singleTypeReference(TypeConstants.INT),
				gen.singleTypeReference(TypeConstants.INT),
				gen.singleTypeReference(TypeConstants.INT),
				gen.qualifiedArrayTypeReference(JAVA_LANG_OBJECT, 1),
		};
		int prefixLen = 0;
		// base arg?
		if (isWrapper && arguments != null)
			newArgs[prefixLen++] = arguments[0];
		// enhancing args:
		for (int i=0; i<this.ENHANCING_ARG_LEN; i++)
			newArgs[i+prefixLen] = gen.argument(
					CharOperation.concat(namePrefix, this.ENHANCING_ARG_NAMES[i]),
					enhanceTypes[i], AccFinal);
		// source args:
		if (arguments != null)
			System.arraycopy(arguments, prefixLen,
							 newArgs, this.ENHANCING_ARG_LEN+prefixLen, arguments.length-prefixLen);

		return newArgs;
	}

	/** Enhance the arguments of a base call or self-call within callin. */
	public static Expression[] enhanceArguments(Expression[] arguments, int pos, WeavingScheme weavingScheme)
	{
		return variants[weavingScheme.ordinal()].internalEnhanceArguments(arguments, pos);
	}
	private Expression[] internalEnhanceArguments(Expression[] arguments, int pos)
	{
		AstGenerator gen = new AstGenerator(pos, pos);
		int fullLen = this.ENHANCING_ARG_LEN;					// enhancing arguments
		if (arguments != null) fullLen += arguments.length; // source arguments?

		Expression[] enhancedArgs = new Expression[fullLen];

		// enhancing arguments:
		for (int i = 0; i < this.ENHANCING_ARG_LEN; i++)
			enhancedArgs[i] = gen.singleNameReference(this.ENHANCING_ARG_NAMES[i]);
		// source arguments?
		if (arguments != null )
			System.arraycopy(arguments, 0, enhancedArgs, this.ENHANCING_ARG_LEN, arguments.length);

		return enhancedArgs;
	}

	// ===== BINDINGS: =====

	/** Enhance a type list with the enhancing argument's types.
	 *
	 * @param scope      needed for lookup of types
	 * @param parameters source parameters
	 * @return new array
	 */
	public static TypeBinding[] enhanceParameters(Scope scope, TypeBinding[] parameters)
	{
		return variants[scope.compilerOptions().weavingScheme.ordinal()].internalEnhanceParameters(scope, parameters);
	}
	private TypeBinding[] internalEnhanceParameters(Scope scope, TypeBinding[] parameters)
	{
		int fullLen = parameters.length + this.ENHANCING_ARG_LEN;

		TypeBinding[] newParameters = new TypeBinding[fullLen];
		// enhancing parameters:
		System.arraycopy(
				getEnhancingArgTypes(scope), 0,
				newParameters, 0, this.ENHANCING_ARG_LEN);
		// source parameters:
		System.arraycopy(
				parameters, 0,
				newParameters, this.ENHANCING_ARG_LEN, parameters.length);

		return newParameters;
	}

	/**
	 * Given that methodDecl is a callin method with base type return, adjust the return type
	 * to j.l.Object, while storing the original returnType via MethodModel.
	 *
	 * @param methodDecl
	 * @param returnType
	 * @return the generalized type (Object)
	 */
	public static TypeBinding generalizeReturnType(MethodDeclaration methodDecl, TypeBinding returnType)
	{
		MethodModel.saveReturnType(methodDecl, returnType);
		returnType= methodDecl.scope.getJavaLangObject();
		methodDecl.returnType.resolvedType= returnType;
		return returnType;
	}

	/**
	 * Prepare for generic handling of return values: all base types are converted to java.lang.Object.
	 * @param orig
	 * @param environment for lookup of java.lang.Object
	 * @return either a binding for java.lang.Object or the original type 'orig'
	 */
	public static TypeBinding getGeneralizedReturnType (TypeBinding orig, LookupEnvironment environment) {
		if (orig.isBaseType())
			return environment.getType(TypeConstants.JAVA_LANG_OBJECT);
		return orig;
	}

	// ===== SIGNATURES (String) =====
//
//	private static char[] enhanceArgsSignature = "[Lorg/objectteams/Team;[IIII[Ljava/lang/Object;".toCharArray();
//
//	public static char[] enhanceSignature(char[] orig)
//	{
//		// disassemble:
//		int rparenPos = CharOperation.indexOf(')', orig);
//		char[] sourceArgs = CharOperation.subarray(orig, 1, rparenPos); // assumes orig[0] == '('
//		// generalize return?
//		char[] returnType;
//		if (rparenPos == orig.length-2) // only one char after ')' means: return is a base type.
//			returnType = OBJECT_SIGNATURE;
//		else
//			returnType = CharOperation.subarray(orig, rparenPos+1, -1);
//
//		// enhance arguments:
//		char[] args = CharOperation.concat(
//				enhanceArgsSignature,
//				sourceArgs);
//
//		// re-assemble
//		return assembleSignature(args, returnType);
//	}
//
//	private static char[] assembleSignature(char[] args, char[] returnType) {
//		char[] result = new char[args.length+returnType.length+2];
//		result[0] = '(';
//		System.arraycopy(args, 0, result, 1, args.length);
//		result[args.length+1] = ')';
//		System.arraycopy(returnType, 0, result, args.length+2, returnType.length);
//		return result;
//	}

	public static char[] generalizeReturnInSignature(char[] signature) {
		int rparenPos = CharOperation.indexOf(')', signature);
		if (rparenPos == signature.length-2) // only one char after ')' means: return is a base type.
			return CharOperation.concat(
							CharOperation.subarray(signature, 0, rparenPos+1),
							OBJECT_SIGNATURE);
		return signature;
	}

	/** If methodDecl is a callin method return just its source-level arguments. */
	public static Argument[] maybeRetrenchArguments(MethodDeclaration methodDecl, WeavingScheme weavingScheme) {
		return variants[weavingScheme.ordinal()].internalMaybeRetrenchArguments(methodDecl);
	}
	private Argument[] internalMaybeRetrenchArguments(MethodDeclaration methodDecl) {
		if (!methodDecl.isCallin() || !internalIsEnhanced(methodDecl))
			return methodDecl.arguments;
		int len = methodDecl.arguments.length;
		Argument[] result = new Argument[len-this.ENHANCING_ARG_LEN];
		System.arraycopy(methodDecl.arguments, this.ENHANCING_ARG_LEN, result, 0, result.length);
		return result;
	}

	public static Expression[] retrenchBasecallArguments(Expression[] arguments, boolean isEnhanced, WeavingScheme weavingScheme) {
		return variants[weavingScheme.ordinal()].internalRetrenchBasecallArguments(arguments, isEnhanced);
	}
	private Expression[] internalRetrenchBasecallArguments(Expression[] arguments, boolean isEnhanced) {
		if (arguments == null) return null;
		int len = arguments.length;
		int discard = this.weavingScheme == WeavingScheme.OTDRE ? 0 : 1; // isSuperAccess (unconditionally) // FIXME(OTDYN) must handle super flag?
		if (isEnhanced)  // if TransformStatementsVisitor has modified this node
			discard += this.ENHANCING_ARG_LEN;
		Expression[] result = new Expression[len-discard];
		System.arraycopy(arguments, discard, result, 0, result.length);
		return result;
	}

	public TypeBinding[] retrenchParameterTypes(TypeBinding[] parameters) {
		if (parameters == null) return null;
		int len = parameters.length;
		if (len >= this.ENHANCING_ARG_LEN) {
			TypeBinding[] result = new TypeBinding[len-this.ENHANCING_ARG_LEN];
			System.arraycopy(parameters, this.ENHANCING_ARG_LEN, result, 0, result.length);
			return result;
		}
		return parameters;
	}

	public static void beautifyTypesString(StringBuffer types, boolean makeShort, WeavingScheme weavingScheme) {
		String typeString = types.toString();
		String prefix =
//{OTDyn: configurable:
			weavingScheme == WeavingScheme.OTDRE 
			? (makeShort 
				? "IBoundBase2, ITeam[], int, int[], int, Object[]"
				: "org.objectteams.IBoundBase2, org.objectteams.ITeam[], int, int[], int, java.lang.Object[]")
// SH}
// {OT/JamVM support:
			: JAMVM_ASPECTBI
			? (makeShort 
					? "Object, int, int, Object[]"
							: "java.lang.Object, int, int, java.lang.Object[]")
// CH}
			: (makeShort 
				? "ITeam[], int[], int, int, int, Object[]"
				: "org.objectteams.ITeam[], int[], int, int, int, java.lang.Object[]");
		if (typeString.startsWith(prefix)) {
			types.delete(0, prefix.length());
			if (types.length()> 0 && types.charAt(0) == ',')
				types.delete(0, 2); // remove leading ", "
		}
	}

	public static Argument[] getSourceArguments(AbstractMethodDeclaration methodDeclaration, WeavingScheme weavingScheme) {
		return variants[weavingScheme.ordinal()].internalGetSourceArguments(methodDeclaration);
	}
	public static Argument[] getSourceArguments(AbstractMethodDeclaration methodDeclaration) {
		WeavingScheme weavingScheme = WeavingScheme.OTRE; // "default" just to avoid null
		if (methodDeclaration.scope != null) {
			weavingScheme = methodDeclaration.scope.compilerOptions().weavingScheme;
		}
		return variants[weavingScheme.ordinal()].internalGetSourceArguments(methodDeclaration);
	}
	private Argument[] internalGetSourceArguments(AbstractMethodDeclaration methodDeclaration) {
		Argument[] arguments = methodDeclaration.arguments;
		if (methodDeclaration.isCallin() && internalIsEnhanced(methodDeclaration)) {
			assert arguments != null;
			int len = arguments.length - this.ENHANCING_ARG_LEN;
			assert len >= 0;
			if (len == 0)
				arguments = null;
			else
				System.arraycopy(
					arguments, this.ENHANCING_ARG_LEN,
					arguments = new Argument[len], 0, len);
		} else if (CharOperation.prefixEquals(IOTConstants.BASE_PREDICATE_PREFIX, methodDeclaration.selector)) {
			// hide base arg of base predicate (has dummy type after parsing):
			return null;
		}
		return arguments;
	}

	public static boolean isEnhanced(AbstractMethodDeclaration methodDeclaration, WeavingScheme weavingScheme) {
		return variants[weavingScheme.ordinal()].internalIsEnhanced(methodDeclaration);
	}
	private boolean internalIsEnhanced(AbstractMethodDeclaration methodDeclaration) {
		Argument[] arguments = methodDeclaration.arguments;
		if (arguments == null || arguments.length < this.ENHANCING_ARG_LEN)
			return false;
		for (int i = 0; i < this.ENHANCING_ARG_LEN; i++) {
			if (!CharOperation.endsWith(arguments[i].name, this.ENHANCING_ARG_NAMES[i]))
				return false;
		}
		return true;
	}

	public static int getEnhancingArgLen(WeavingScheme weavingScheme) {
		return variants[weavingScheme.ordinal()].ENHANCING_ARG_LEN;
	}
}
