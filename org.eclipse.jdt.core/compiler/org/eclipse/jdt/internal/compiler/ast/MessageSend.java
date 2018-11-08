/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Nick Teryaev - fix for bug (https://bugs.eclipse.org/bugs/show_bug.cgi?id=40752)
 *     Fraunhofer FIRST - extended API and implementation
 *     Technical University Berlin - extended API and implementation
 *     Stephan Herrmann - Contributions for
 *								bug 319201 - [null] no warning when unboxing SingleNameReference causes NPE
 *								bug 349326 - [1.7] new warning for missing try-with-resources
 *								bug 186342 - [compiler][null] Using annotations for null checking
 *								bug 358903 - Filter practically unimportant resource leak warnings
 *								bug 370639 - [compiler][resource] restore the default for resource leak warnings
 *								bug 345305 - [compiler][null] Compiler misidentifies a case of "variable can only be null"
 *								bug 388996 - [compiler][resource] Incorrect 'potential resource leak'
 *								bug 379784 - [compiler] "Method can be static" is not getting reported
 *								bug 379834 - Wrong "method can be static" in presence of qualified super and different staticness of nested super class.
 *								bug 388281 - [compiler][null] inheritance of null annotations as an option
 *								bug 392862 - [1.8][compiler][null] Evaluate null annotations on array types
 *								bug 394768 - [compiler][resource] Incorrect resource leak warning when creating stream in conditional
 *								bug 381445 - [compiler][resource] Can the resource leak check be made aware of Closeables.closeQuietly?
 *								bug 331649 - [compiler][null] consider null annotations for fields
 *								bug 383368 - [compiler][null] syntactic null analysis for field references
 *								bug 382069 - [null] Make the null analysis consider JUnit's assertNotNull similarly to assertions
 *								bug 382350 - [1.8][compiler] Unable to invoke inherited default method via I.super.m() syntax
 *								bug 404649 - [1.8][compiler] detect illegal reference to indirect or redundant super
 *								bug 403086 - [compiler][null] include the effect of 'assert' in syntactic null analysis for fields
 *								bug 403147 - [compiler][null] FUP of bug 400761: consolidate interaction between unboxing, NPE, and deferred checking
 *								Bug 392099 - [1.8][compiler][null] Apply null annotation on types for null analysis
 *								Bug 415043 - [1.8][null] Follow-up re null type annotations after bug 392099
 *								Bug 405569 - Resource leak check false positive when using DbUtils.closeQuietly
 *								Bug 411964 - [1.8][null] leverage null type annotation in foreach statement
 *								Bug 417295 - [1.8[[null] Massage type annotated null analysis to gel well with deep encoded type bindings.
 *								Bug 400874 - [1.8][compiler] Inference infrastructure should evolve to meet JLS8 18.x (Part G of JSR335 spec)
 *								Bug 423504 - [1.8] Implement "18.5.3 Functional Interface Parameterization Inference"
 *								Bug 424710 - [1.8][compiler] CCE in SingleNameReference.localVariableBinding
 *								Bug 425152 - [1.8] [compiler] Lambda Expression not resolved but flow analyzed leading to NPE.
 *								Bug 424205 - [1.8] Cannot infer type for diamond type with lambda on method invocation
 *								Bug 424415 - [1.8][compiler] Eventual resolution of ReferenceExpression is not seen to be happening.
 *								Bug 426366 - [1.8][compiler] Type inference doesn't handle multiple candidate target types in outer overload context
 *								Bug 426290 - [1.8][compiler] Inference + overloading => wrong method resolution ?
 *								Bug 427483 - [Java 8] Variables in lambdas sometimes can't be resolved
 *								Bug 427438 - [1.8][compiler] NPE at org.eclipse.jdt.internal.compiler.ast.ConditionalExpression.generateCode(ConditionalExpression.java:280)
 *								Bug 426996 - [1.8][inference] try to avoid method Expression.unresolve()? 
 *								Bug 428352 - [1.8][compiler] Resolution errors don't always surface
 *								Bug 429430 - [1.8] Lambdas and method reference infer wrong exception type with generics (RuntimeException instead of IOException)
 *								Bug 441734 - [1.8][inference] Generic method with nested parameterized type argument fails on method reference
 *								Bug 452788 - [1.8][compiler] Type not correctly inferred in lambda expression
 *								Bug 456487 - [1.8][null] @Nullable type variant of @NonNull-constrained type parameter causes grief
 *								Bug 407414 - [compiler][null] Incorrect warning on a primitive type being null
 *								Bug 472618 - [compiler][null] assertNotNull vs. Assert.assertNotNull
 *								Bug 470958 - [1.8] Unable to convert lambda 
 *								Bug 410218 - Optional warning for arguments of "unexpected" types to Map#get(Object), Collection#remove(Object) et al.
 *     Jesper S Moller - Contributions for
 *								Bug 378674 - "The method can be declared as static" is wrong
 *        Andy Clement (GoPivotal, Inc) aclement@gopivotal.com - Contributions for
 *                          Bug 383624 - [1.8][compiler] Revive code generation support for type annotations (from Olivier's work)
 *                          Bug 409245 - [1.8][compiler] Type annotations dropped when call is routed through a synthetic bridge method
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.ast;

import static org.eclipse.jdt.internal.compiler.ast.ExpressionContext.*;
import static org.eclipse.objectteams.otdt.core.compiler.IOTConstants.CALLIN_FLAG_DEFINITELY_MISSING_BASECALL;
import static org.eclipse.objectteams.otdt.core.compiler.IOTConstants.CALLIN_FLAG_POTENTIALLY_MISSING_BASECALL;
import static org.eclipse.objectteams.otdt.internal.core.compiler.lookup.SyntheticOTTargetMethod.OTDREMethodDecapsulation;

import java.util.HashMap;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration.WrapperKind;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.codegen.Opcodes;
import org.eclipse.jdt.internal.compiler.flow.FlowContext;
import org.eclipse.jdt.internal.compiler.flow.FlowInfo;
import org.eclipse.jdt.internal.compiler.flow.UnconditionalFlowInfo;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.impl.IrritantSet;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions.WeavingScheme;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.ImplicitNullAnnotationVerifier;
import org.eclipse.jdt.internal.compiler.lookup.InferenceContext18;
import org.eclipse.jdt.internal.compiler.lookup.InferenceVariable;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.MissingTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedGenericMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.PolyParameterizedGenericMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.PolyTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.PolymorphicMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReasons;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.RawTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TagBits;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.eclipse.jdt.internal.compiler.util.SimpleLookupTable;
import org.eclipse.objectteams.otdt.core.compiler.IOTConstants;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.BaseCallMessageSend;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.BaseReference;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.CalloutMappingDeclaration;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.Dependencies;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.ITranslationStates;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.AnchorMapping;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.DependentTypeBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.SyntheticRoleBridgeMethodBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.WeakenedTypeBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.mappings.CalloutImplementor;
import org.eclipse.objectteams.otdt.internal.core.compiler.mappings.CalloutImplementorDyn;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.MethodModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.TeamModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.copyinheritance.CopyInheritance.RoleConstructorCall;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.StandardElementGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.RoleTypeCreator;

/**
 * OTDT changes:
 *
 * DECAPSULATION:
 * ==============
 * What: Decapsulation ignore private
 * How:  Avoid special treatment for private methods if decapsulation is involved:
 * 		+ generateCode() must not use invokespecial
 * 		+ manageSyntheticIfNecessary() must not create an access method
 *
 *
 * ROLE CLASS/INTERFACE:
 * =====================
 * What: Use invokeinterface for role methods
 * Why:  These methods are dispatched using the role interface of a RoleTypeBinding.
 *
 * Note: Each client generating a message send is responsible for providing a
 *       MethodBinding with declaringClass correctly set, since class/ifc adjustment
 *       is not performed for generated methods!
 *
 * FURTHER RESOLVE ISSUES:
 * =======================
 * What: Avoid double-resolving for receiver if this send is transformed during resolve.
 *
 * What: Check base calls: name&signature must match the enclosing method.
 *
 * What: Wrap return type, possibly using receiver to instantiate the type anchor.
 *
 *
 * Bracket lookup of the method binding with {before,after}MethodLookup
 * ------
 * What: Need to setup AnchorMapping
 * Why:  During method lookup, arguments may need to be instantiated using
 *       the actual receiver and arguments.
 *
 * What: May need to redirect static calls to the enclosing team.
 * How:  Invoke checkRedirectStatic.
 * Why:  Only after resolving receiver we know whether it is a type reference
 * 		 (and hence the method a static method).
 *
 * What: Redirect methods from predefined Team.Confined (afterMethodLookup)
 * Why:  Some methods are mentioned in Team.Confined but only for importing these
 * 	     from Object.
 *
 * What: Additional checking for anchored role type arguments
 * Why:  Cannot report errors during method lookup, because all potential
 *       matches are checked (not all of these should count for errors).
 *
 * What: Work against signature weakening.
 * How:  Insert casts for return value if needed.
 *
 */
public class MessageSend extends Expression implements IPolyExpression, Invocation {

	public Expression receiver;
	public char[] selector;
	public Expression[] arguments;
	public MethodBinding binding;							// exact binding resulting from lookup
	public MethodBinding syntheticAccessor;						// synthetic accessor for inner-emulation
	public TypeBinding expectedType;					// for generic method invocation (return type inference)

	public long nameSourcePosition ; //(start<<32)+end

	public TypeBinding actualReceiverType;
	public TypeBinding valueCast; // extra reference type cast to perform on method returned value
	public TypeReference[] typeArguments;
	public TypeBinding[] genericTypeArguments;
	public ExpressionContext expressionContext = VANILLA_CONTEXT;

	 // hold on to this context from invocation applicability inference until invocation type inference (per method candidate):
	private SimpleLookupTable/*<PGMB,InferenceContext18>*/ inferenceContexts;
	private HashMap<TypeBinding, MethodBinding> solutionsPerTargetType;
	private InferenceContext18 outerInferenceContext; // resolving within the context of an outer (lambda) inference?
	
	private boolean receiverIsType;
	protected boolean argsContainCast;
	public TypeBinding[] argumentTypes = Binding.NO_PARAMETERS;
	public boolean argumentsHaveErrors = false;
	

//{ObjectTeams:
	private boolean isDecapsulation = false;
	public int accessId; // for decaps access under OTREDyn
	// special case: the role method call in a method pushed out to the enclosing team needs special resolving
	public boolean isPushedOutRoleMethodCall = false;
	public boolean isGenerated = false;
// SH}

@Override
public FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo) {
	boolean nonStatic = !this.binding.isStatic();
	boolean wasInsideAssert = ((flowContext.tagBits & FlowContext.HIDE_NULL_COMPARISON_WARNING) != 0);
	flowInfo = this.receiver.analyseCode(currentScope, flowContext, flowInfo, nonStatic).unconditionalInits();

	// recording the closing of AutoCloseable resources:
	CompilerOptions compilerOptions = currentScope.compilerOptions();
	boolean analyseResources = compilerOptions.analyseResourceLeaks;
	if (analyseResources) {
		if (nonStatic) {
			// closeable.close()
			if (CharOperation.equals(TypeConstants.CLOSE, this.selector)) {
				recordCallingClose(currentScope, flowContext, flowInfo, this.receiver);
			}
		} else if (this.arguments != null && this.arguments.length > 0 && FakedTrackingVariable.isAnyCloseable(this.arguments[0].resolvedType)) {
			// Helper.closeMethod(closeable, ..)
			for (int i=0; i<TypeConstants.closeMethods.length; i++) {
				CloseMethodRecord record = TypeConstants.closeMethods[i];
				if (CharOperation.equals(record.selector, this.selector)
						&& CharOperation.equals(record.typeName, this.binding.declaringClass.compoundName)) 
				{
					int len = Math.min(record.numCloseableArgs, this.arguments.length);
					for (int j=0; j<len; j++)
						recordCallingClose(currentScope, flowContext, flowInfo, this.arguments[j]);
					break;
				}
			}
		}
	}
	if (compilerOptions.isAnyEnabled(IrritantSet.UNLIKELY_ARGUMENT_TYPE) && this.binding.isValidBinding()
			&& this.arguments != null) {
		if (this.arguments.length == 1 && !this.binding.isStatic()) {
			UnlikelyArgumentCheck argumentChecks = UnlikelyArgumentCheck.determineCheckForNonStaticSingleArgumentMethod(
				this.argumentTypes[0], currentScope, this.selector, this.actualReceiverType, this.binding.parameters);

			if (argumentChecks != null && argumentChecks.isDangerous(currentScope)) {
				currentScope.problemReporter().unlikelyArgumentType(this.arguments[0], this.binding,
						this.argumentTypes[0], argumentChecks.typeToReport, argumentChecks.dangerousMethod);
			}
 		} else if (this.arguments.length == 2 && this.binding.isStatic()) {
			UnlikelyArgumentCheck argumentChecks = UnlikelyArgumentCheck.determineCheckForStaticTwoArgumentMethod(
				this.argumentTypes[1], currentScope, this.selector, this.argumentTypes[0],
				this.binding.parameters, this.actualReceiverType);

			if (argumentChecks != null && argumentChecks.isDangerous(currentScope)) {
				currentScope.problemReporter().unlikelyArgumentType(this.arguments[1], this.binding,
						this.argumentTypes[1], argumentChecks.typeToReport, argumentChecks.dangerousMethod);
			}
 		}
	}

	if (nonStatic) {
		int timeToLive = ((this.bits & ASTNode.InsideExpressionStatement) != 0) ? 3 : 2;
		this.receiver.checkNPE(currentScope, flowContext, flowInfo, timeToLive);
	}

	if (this.arguments != null) {
		int length = this.arguments.length;
		for (int i = 0; i < length; i++) {
			Expression argument = this.arguments[i];
			argument.checkNPEbyUnboxing(currentScope, flowContext, flowInfo);
			switch (detectAssertionUtility(i)) {
				case TRUE_ASSERTION:
					flowInfo = analyseBooleanAssertion(currentScope, argument, flowContext, flowInfo, wasInsideAssert, true);
					break;
				case FALSE_ASSERTION:
					flowInfo = analyseBooleanAssertion(currentScope, argument, flowContext, flowInfo, wasInsideAssert, false);
					break;
				case NONNULL_ASSERTION:
					flowInfo = analyseNullAssertion(currentScope, argument, flowContext, flowInfo, false);
					break;
				case NULL_ASSERTION:
					flowInfo = analyseNullAssertion(currentScope, argument, flowContext, flowInfo, true);
					break;
				default:
					flowInfo = argument.analyseCode(currentScope, flowContext, flowInfo).unconditionalInits();
			}
			if (analyseResources) {
				// if argument is an AutoCloseable insert info that it *may* be closed (by the target method, i.e.)
				flowInfo = FakedTrackingVariable.markPassedToOutside(currentScope, argument, flowInfo, flowContext, false);
			}
		}
		analyseArguments(currentScope, flowContext, flowInfo, this.binding, this.arguments);
	}
	ReferenceBinding[] thrownExceptions;
	if ((thrownExceptions = this.binding.thrownExceptions) != Binding.NO_EXCEPTIONS) {
		if ((this.bits & ASTNode.Unchecked) != 0 && this.genericTypeArguments == null) {
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=277643, align with javac on JLS 15.12.2.6
			thrownExceptions = currentScope.environment().convertToRawTypes(this.binding.thrownExceptions, true, true);
		}
		// must verify that exceptions potentially thrown by this expression are caught in the method
		flowContext.checkExceptionHandlers(thrownExceptions, this, flowInfo.copy(), currentScope);
		// TODO (maxime) the copy above is needed because of a side effect into
		//               checkExceptionHandlers; consider protecting there instead of here;
		//               NullReferenceTest#test0510
	}
	manageSyntheticAccessIfNecessary(currentScope, flowInfo);
//{ObjectTeams: base calls via super:
	flowInfo = checkBaseCallsIfSuper(currentScope, flowInfo);
// SH}
	// account for pot. exceptions thrown by method execution
	flowContext.recordAbruptExit();
	flowContext.expireNullCheckedFieldInfo(); // no longer trust this info after any message send
	return flowInfo;
}
private void recordCallingClose(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo, Expression closeTarget) {
	FakedTrackingVariable trackingVariable = FakedTrackingVariable.getCloseTrackingVariable(closeTarget, flowInfo, flowContext);
	if (trackingVariable != null) { // null happens if target is not a local variable or not an AutoCloseable
		if (trackingVariable.methodScope == currentScope.methodScope()) {
			trackingVariable.markClose(flowInfo, flowContext);
		} else {
			trackingVariable.markClosedInNestedMethod();
		}
	}
}

//{ObjectTeams: checkBaseCallsIfSuper
protected FlowInfo checkBaseCallsIfSuper(BlockScope currentScope, FlowInfo flowInfo) {
	MethodScope methodScope = currentScope.methodScope();
	if (methodScope == null) 
		return flowInfo;
	AbstractMethodDeclaration methodDecl = methodScope.referenceMethod();
	if (methodDecl == null || !methodDecl.isCallin()) 
		return flowInfo;
	if (!this.isSuperAccess())
		return flowInfo;
	MethodDeclaration callinMethod = (MethodDeclaration) methodDecl;
	if (MethodModel.hasCallinFlag(this.binding, CALLIN_FLAG_DEFINITELY_MISSING_BASECALL))
		return flowInfo; // no effect
	
	boolean definitelyViaSuper = !MethodModel.hasCallinFlag(this.binding, CALLIN_FLAG_POTENTIALLY_MISSING_BASECALL);
		
	LocalVariableBinding trackingVariable = callinMethod.baseCallTrackingVariable.binding;
	if (flowInfo.isDefinitelyAssigned(callinMethod.baseCallTrackingVariable)) {
		if (definitelyViaSuper)
			currentScope.problemReporter().definitelyDuplicateBasecall(this);
		else
			currentScope.problemReporter().potentiallyDuplicateBasecall(this);
	} else if (flowInfo.isPotentiallyAssigned(trackingVariable)) {
		currentScope.problemReporter().potentiallyDuplicateBasecall(this);
	} else {
		if (definitelyViaSuper) {
			flowInfo.markAsDefinitelyAssigned(trackingVariable);
		} else {
			FlowInfo potential = flowInfo.copy();
			potential.markAsDefinitelyAssigned(trackingVariable);
			flowInfo = FlowInfo.conditional(flowInfo.initsWhenTrue(), potential.initsWhenTrue());
		}
	}
	return flowInfo;
}
// SH}

// classification of well-known assertion utilities:
private static final int TRUE_ASSERTION = 1;
private static final int FALSE_ASSERTION = 2;
private static final int NULL_ASSERTION = 3;
private static final int NONNULL_ASSERTION = 4;

// is the argument at the given position being checked by a well-known assertion utility?
// if so answer what kind of assertion we are facing.
private int detectAssertionUtility(int argumentIdx) {
	TypeBinding[] parameters = this.binding.original().parameters;
	if (argumentIdx < parameters.length) {
		TypeBinding parameterType = parameters[argumentIdx];
		TypeBinding declaringClass = this.binding.declaringClass;
		if (declaringClass != null && parameterType != null) {
			switch (declaringClass.id) {
				case TypeIds.T_OrgEclipseCoreRuntimeAssert:
					if (parameterType.id == TypeIds.T_boolean)
						return TRUE_ASSERTION;
					if (parameterType.id == TypeIds.T_JavaLangObject && CharOperation.equals(TypeConstants.IS_NOTNULL, this.selector))
						return NONNULL_ASSERTION;
					break;
				case TypeIds.T_JunitFrameworkAssert:
				case TypeIds.T_OrgJunitAssert:
					if (parameterType.id == TypeIds.T_boolean) {
						if (CharOperation.equals(TypeConstants.ASSERT_TRUE, this.selector))
							return TRUE_ASSERTION;
						if (CharOperation.equals(TypeConstants.ASSERT_FALSE, this.selector))
							return FALSE_ASSERTION;
					} else if (parameterType.id == TypeIds.T_JavaLangObject) {
						if (CharOperation.equals(TypeConstants.ASSERT_NOTNULL, this.selector))
							return NONNULL_ASSERTION;
						if (CharOperation.equals(TypeConstants.ASSERT_NULL, this.selector))
							return NULL_ASSERTION;
					}
					break;
				case TypeIds.T_OrgApacheCommonsLangValidate:
					if (parameterType.id == TypeIds.T_boolean) {
						if (CharOperation.equals(TypeConstants.IS_TRUE, this.selector))
							return TRUE_ASSERTION;
					} else if (parameterType.id == TypeIds.T_JavaLangObject) {
						if (CharOperation.equals(TypeConstants.NOT_NULL, this.selector))
							return NONNULL_ASSERTION;
					}
					break;
				case TypeIds.T_OrgApacheCommonsLang3Validate:
					if (parameterType.id == TypeIds.T_boolean) {
						if (CharOperation.equals(TypeConstants.IS_TRUE, this.selector))
							return TRUE_ASSERTION;
					} else if (parameterType.isTypeVariable()) {
						if (CharOperation.equals(TypeConstants.NOT_NULL, this.selector))
							return NONNULL_ASSERTION;
					}
					break;
				case TypeIds.T_ComGoogleCommonBasePreconditions:
					if (parameterType.id == TypeIds.T_boolean) {
						if (CharOperation.equals(TypeConstants.CHECK_ARGUMENT, this.selector)
							|| CharOperation.equals(TypeConstants.CHECK_STATE, this.selector))
							return TRUE_ASSERTION;
					} else if (parameterType.isTypeVariable()) {
						if (CharOperation.equals(TypeConstants.CHECK_NOT_NULL, this.selector))
							return NONNULL_ASSERTION;
					}
					break;					
				case TypeIds.T_JavaUtilObjects:
					if (parameterType.isTypeVariable()) {
						if (CharOperation.equals(TypeConstants.REQUIRE_NON_NULL, this.selector))
							return NONNULL_ASSERTION;
					}
					break;					
			}
		}
	}
	return 0;
}

private FlowInfo analyseBooleanAssertion(BlockScope currentScope, Expression argument,
		FlowContext flowContext, FlowInfo flowInfo, boolean wasInsideAssert, boolean passOnTrue)
{
	Constant cst = argument.optimizedBooleanConstant();
	boolean isOptimizedTrueAssertion = cst != Constant.NotAConstant && cst.booleanValue() == true;
	boolean isOptimizedFalseAssertion = cst != Constant.NotAConstant && cst.booleanValue() == false;
	int tagBitsSave = flowContext.tagBits;
	flowContext.tagBits |= FlowContext.HIDE_NULL_COMPARISON_WARNING;
	if (!passOnTrue)
		flowContext.tagBits |= FlowContext.INSIDE_NEGATION; // this affects syntactic analysis for fields in EqualExpression
	FlowInfo conditionFlowInfo = argument.analyseCode(currentScope, flowContext, flowInfo.copy());
	flowContext.extendTimeToLiveForNullCheckedField(2); // survive this assert as a MessageSend and as a Statement
	flowContext.tagBits = tagBitsSave;

	UnconditionalFlowInfo assertWhenPassInfo;
	FlowInfo assertWhenFailInfo;
	boolean isOptimizedPassing;
	boolean isOptimizedFailing;
	if (passOnTrue) {
		assertWhenPassInfo = conditionFlowInfo.initsWhenTrue().unconditionalInits();
		assertWhenFailInfo = conditionFlowInfo.initsWhenFalse();
		isOptimizedPassing = isOptimizedTrueAssertion;
		isOptimizedFailing = isOptimizedFalseAssertion;
	} else {
		assertWhenPassInfo = conditionFlowInfo.initsWhenFalse().unconditionalInits();
		assertWhenFailInfo = conditionFlowInfo.initsWhenTrue();
		isOptimizedPassing = isOptimizedFalseAssertion;
		isOptimizedFailing = isOptimizedTrueAssertion;
	}
	if (isOptimizedPassing) {
		assertWhenFailInfo.setReachMode(FlowInfo.UNREACHABLE_OR_DEAD);
	}
	if (!isOptimizedFailing) {
		// if assertion is not failing for sure, only then it makes sense to carry the flow info ahead.
		// if the code does reach ahead, it means the assert didn't cause an exit, and so
		// the expression inside it shouldn't change the prior flowinfo
		// viz. org.eclipse.core.runtime.Assert.isLegal(false && o != null)
		
		// keep the merge from the initial code for the definite assignment
		// analysis, tweak the null part to influence nulls downstream
		flowInfo = flowInfo.mergedWith(assertWhenFailInfo.nullInfoLessUnconditionalCopy()).
			addInitializationsFrom(assertWhenPassInfo.discardInitializationInfo());
	}
	return flowInfo;
}
private FlowInfo analyseNullAssertion(BlockScope currentScope, Expression argument,
		FlowContext flowContext, FlowInfo flowInfo, boolean expectingNull)
{
	int nullStatus = argument.nullStatus(flowInfo, flowContext);
	boolean willFail = (nullStatus == (expectingNull ? FlowInfo.NON_NULL : FlowInfo.NULL));
	flowInfo = argument.analyseCode(currentScope, flowContext, flowInfo).unconditionalInits();
	LocalVariableBinding local = argument.localVariableBinding();
	if (local != null) {// beyond this point the argument can only be null/nonnull
		if (expectingNull) 
			flowInfo.markAsDefinitelyNull(local);
		else 
			flowInfo.markAsDefinitelyNonNull(local);
	} else {
		if (!expectingNull
			&& argument instanceof Reference 
			&& currentScope.compilerOptions().enableSyntacticNullAnalysisForFields) 
		{
			FieldBinding field = ((Reference)argument).lastFieldBinding();
			if (field != null && (field.type.tagBits & TagBits.IsBaseType) == 0) {
				flowContext.recordNullCheckedFieldReference((Reference) argument, 3); // survive this assert as a MessageSend and as a Statement
			}
		}
	}
	if (willFail)
		flowInfo.setReachMode(FlowInfo.UNREACHABLE_BY_NULLANALYSIS);
	return flowInfo;
}

@Override
public boolean checkNPE(BlockScope scope, FlowContext flowContext, FlowInfo flowInfo, int ttlForFieldCheck) {
	// message send as a receiver
	int nullStatus = nullStatus(flowInfo, flowContext); // note that flowInfo is not used inside nullStatus(..)
	if ((nullStatus & FlowInfo.POTENTIALLY_NULL) != 0) {
		if(this.binding.returnType.isTypeVariable() && nullStatus == FlowInfo.FREE_TYPEVARIABLE && scope.environment().globalOptions.pessimisticNullAnalysisForFreeTypeVariablesEnabled) {
			scope.problemReporter().methodReturnTypeFreeTypeVariableReference(this.binding, this);			
		} else {
			scope.problemReporter().messageSendPotentialNullReference(this.binding, this);
		}
	} else if ((this.resolvedType.tagBits & TagBits.AnnotationNonNull) != 0) {
		NullAnnotationMatching nonNullStatus = NullAnnotationMatching.okNonNullStatus(this);
		if (nonNullStatus.wantToReport())
			nonNullStatus.report(scope);
	}
	return true; // done all possible checking
}
/**
 * @see org.eclipse.jdt.internal.compiler.ast.Expression#computeConversion(org.eclipse.jdt.internal.compiler.lookup.Scope, org.eclipse.jdt.internal.compiler.lookup.TypeBinding, org.eclipse.jdt.internal.compiler.lookup.TypeBinding)
 */
@Override
public void computeConversion(Scope scope, TypeBinding runtimeTimeType, TypeBinding compileTimeType) {
	if (runtimeTimeType == null || compileTimeType == null)
		return;
	// set the generic cast after the fact, once the type expectation is fully known (no need for strict cast)
	if (this.binding != null && this.binding.isValidBinding()) {
		MethodBinding originalBinding = this.binding.original();
		TypeBinding originalType = originalBinding.returnType;
	    // extra cast needed if method return type is type variable
		if (ArrayBinding.isArrayClone(this.actualReceiverType, this.binding)
				&& runtimeTimeType.id != TypeIds.T_JavaLangObject
				&& scope.compilerOptions().sourceLevel >= ClassFileConstants.JDK1_5) {
			// from 1.5 source level on, array#clone() resolves to array type, but codegen to #clone()Object - thus require extra inserted cast
			this.valueCast = runtimeTimeType;
		} else if (originalType.leafComponentType().isTypeVariable()) {
	    	TypeBinding targetType = (!compileTimeType.isBaseType() && runtimeTimeType.isBaseType())
	    		? compileTimeType  // unboxing: checkcast before conversion
	    		: runtimeTimeType;
	        this.valueCast = originalType.genericCast(targetType);
//{ObjectTeams: don't try to let casting do the job of lowering:
	        TypeBinding leafCompile = compileTimeType.leafComponentType();
	        TypeBinding leafRuntime = runtimeTimeType.leafComponentType();
	        if ((leafCompile instanceof ReferenceBinding) && (leafRuntime instanceof ReferenceBinding)
	        		&& ((ReferenceBinding)leafCompile).isCompatibleViaLowering((ReferenceBinding) leafRuntime))
	        	this.valueCast = originalType.genericCast(compileTimeType);	        	
// SH}
		}
        if (this.valueCast instanceof ReferenceBinding) {
			ReferenceBinding referenceCast = (ReferenceBinding) this.valueCast;
			if (!referenceCast.canBeSeenBy(scope)) {
//{ObjectTeams: Decapsulation:
			  if(!isDecapsulationAllowed(scope))
// SH}
	        	scope.problemReporter().invalidType(this,
	        			new ProblemReferenceBinding(
							CharOperation.splitOn('.', referenceCast.shortReadableName()),
							referenceCast,
							ProblemReasons.NotVisible));
			}
        }
	}
	super.computeConversion(scope, runtimeTimeType, compileTimeType);
}

/**
 * MessageSend code generation
 *
 * @param currentScope org.eclipse.jdt.internal.compiler.lookup.BlockScope
 * @param codeStream org.eclipse.jdt.internal.compiler.codegen.CodeStream
 * @param valueRequired boolean
 */
@Override
public void generateCode(BlockScope currentScope, CodeStream codeStream, boolean valueRequired) {
	cleanUpInferenceContexts();
	int pc = codeStream.position;
	// generate receiver/enclosing instance access
	MethodBinding codegenBinding = this.binding instanceof PolymorphicMethodBinding ? this.binding : this.binding.original();
	boolean isStatic = codegenBinding.isStatic();
	if (isStatic) {
		this.receiver.generateCode(currentScope, codeStream, false);
	} else if ((this.bits & ASTNode.DepthMASK) != 0 && this.receiver.isImplicitThis()) { // outer access ?
		// outer method can be reached through emulation if implicit access
		ReferenceBinding targetType = currentScope.enclosingSourceType().enclosingTypeAt((this.bits & ASTNode.DepthMASK) >> ASTNode.DepthSHIFT);
		Object[] path = currentScope.getEmulationPath(targetType, true /*only exact match*/, false/*consider enclosing arg*/);
		codeStream.generateOuterAccess(path, this, targetType, currentScope);
	} else {
		if(this.receiver instanceof BaseReference) {
//			NOP - we generate an invokedynamic
		} else {
			this.receiver.generateCode(currentScope, codeStream, true);
			if ((this.bits & NeedReceiverGenericCast) != 0) {
				codeStream.checkcast(this.actualReceiverType);
			}
		}
	}
//{ObjectTeams: various synthetic arguments for static role methods:
	// role method bridge needs role arg (null)
	if (   this.syntheticAccessor instanceof SyntheticRoleBridgeMethodBinding 
		&& !this.syntheticAccessor.isStatic() // non-static accessor ...
		&& this.binding.isStatic())  		  // towards static role method
	{
		codeStream.aload_0();   	// receiver: team instance
		codeStream.aconst_null(); 	// arg: no role instance
		codeStream.iconst_0(); 		// arg: dummy 
		codeStream.aload_0();		// arg: team instance
		// directly use the accessor and its declaring class for the invoke instruction:
		this.binding = this.syntheticAccessor;
		this.actualReceiverType = this.syntheticAccessor.declaringClass;
		this.syntheticAccessor = null;
		codegenBinding = this.binding;
		isStatic = false;
	}
	// requiring an enclosing team instance?
	// cf. AbstractQualifiedAllocationExpression.generateCode()
	else if (codegenBinding.needsSyntheticEnclosingTeamInstance())
	{
		codeStream.iconst_0(); // dummy
		codeStream.generateSyntheticEnclosingInstanceValues(
				currentScope,
				(ReferenceBinding)this.actualReceiverType,
				null, /*enclosing instance*/
				this);
	}
// SH}
	codeStream.recordPositionsFrom(pc, this.sourceStart);
	// generate arguments
	generateArguments(this.binding, this.arguments, currentScope, codeStream);
	pc = codeStream.position;
	
	if(this.receiver instanceof BaseReference) {
		CallNextInvokeDynamicExpression expr = new CallNextInvokeDynamicExpression((BaseReference)this.receiver, this.binding, codegenBinding.parameters);
		final int bootStrapIndex = codeStream.classFile.recordBootstrapMethod(expr);
		final char[] bootstrapSelector = "callNext".toCharArray(); //$NON-NLS-1$
		final int bootstrapArgumentCount = 5;
		codeStream.invokeDynamic(bootStrapIndex, bootstrapArgumentCount, 1, bootstrapSelector, codegenBinding.signature()); 
	} else {
	
	// actual message invocation
	if (this.syntheticAccessor == null){
		TypeBinding constantPoolDeclaringClass = CodeStream.getConstantPoolDeclaringClass(currentScope, codegenBinding, this.actualReceiverType, this.receiver.isImplicitThis());
		if (isStatic){
//{ObjectTeams: role method via the class-part:
			if (constantPoolDeclaringClass.isRole())
				constantPoolDeclaringClass = ((ReferenceBinding)constantPoolDeclaringClass).getRealClass();
// SH}
			codeStream.invoke(Opcodes.OPC_invokestatic, codegenBinding, constantPoolDeclaringClass, this.typeArguments);
//{ObjectTeams: decapsulated methods will not be private in the JVM any more:
/* orig:
		} else if((this.receiver.isSuper()) || codegenBinding.isPrivate()){
  :giro */
		} else if((this.receiver.isSuper()) || (codegenBinding.isPrivate() && !this.isDecapsulation)) 
		{
// SH}
			codeStream.invoke(Opcodes.OPC_invokespecial, codegenBinding, constantPoolDeclaringClass, this.typeArguments);
//{ObjectTeams: always use interface methods of role type binding:
/* orig:
		} else if (constantPoolDeclaringClass.isInterface()) { // interface or annotation type
  :giro */
		} else if (constantPoolDeclaringClass.isInterface()  // interface or annotation type
				   || constantPoolDeclaringClass.isRoleType())
		{
// SH}
			codeStream.invoke(Opcodes.OPC_invokeinterface, codegenBinding, constantPoolDeclaringClass, this.typeArguments);
		} else {
			codeStream.invoke(Opcodes.OPC_invokevirtual, codegenBinding, constantPoolDeclaringClass, this.typeArguments);
		}
	} else {
		codeStream.invoke(Opcodes.OPC_invokestatic, this.syntheticAccessor, null /* default declaringClass */, this.typeArguments);
	}
	
	}
	// required cast must occur even if no value is required
	if (this.valueCast != null) codeStream.checkcast(this.valueCast);
	if (valueRequired){
		// implicit conversion if necessary
		codeStream.generateImplicitConversion(this.implicitConversion);
	} else {
		boolean isUnboxing = (this.implicitConversion & TypeIds.UNBOXING) != 0;
		// conversion only generated if unboxing
		if (isUnboxing) codeStream.generateImplicitConversion(this.implicitConversion);
		switch (isUnboxing ? postConversionType(currentScope).id : codegenBinding.returnType.id) {
			case T_long :
			case T_double :
				codeStream.pop2();
				break;
			case T_void :
				break;
			default :
				codeStream.pop();
		}
	}
	codeStream.recordPositionsFrom(pc, (int)(this.nameSourcePosition >>> 32)); // highlight selector
}
/**
 * @see org.eclipse.jdt.internal.compiler.lookup.InvocationSite#genericTypeArguments()
 */
@Override
public TypeBinding[] genericTypeArguments() {
	return this.genericTypeArguments;
}

@Override
public boolean isSuperAccess() {
	return this.receiver.isSuper();
}
//{ObjectTeams: super OR tsuper?
protected boolean isAnySuperAccess() {
	return this.receiver.isSuper();
}
// SH}
@Override
public boolean isTypeAccess() {
	return this.receiver != null && this.receiver.isTypeReference();
}
public void manageSyntheticAccessIfNecessary(BlockScope currentScope, FlowInfo flowInfo){

	if ((flowInfo.tagBits & FlowInfo.UNREACHABLE_OR_DEAD) != 0)	return;

	// if method from parameterized type got found, use the original method at codegen time
	MethodBinding codegenBinding = this.binding.original();
	if (this.binding.isPrivate()
//{ObjectTeams:
		&& this.syntheticAccessor == null // may be pre-set by CallinImplementorDyn
		&& !this.isDecapsulation)
// SH}
	{

		// depth is set for both implicit and explicit access (see MethodBinding#canBeSeenBy)
		if (TypeBinding.notEquals(currentScope.enclosingSourceType(), codegenBinding.declaringClass)){
			this.syntheticAccessor = ((SourceTypeBinding)codegenBinding.declaringClass).addSyntheticMethod(codegenBinding, false /* not super access there */);
			currentScope.problemReporter().needToEmulateMethodAccess(codegenBinding, this);
			return;
		}

	} else if (this.receiver instanceof QualifiedSuperReference) { 	// qualified super
		if (this.actualReceiverType.isInterface()) 
			return; // invoking an overridden default method, which is accessible/public by definition
		// qualified super need emulation always
		SourceTypeBinding destinationType = (SourceTypeBinding)(((QualifiedSuperReference)this.receiver).currentCompatibleType);
		this.syntheticAccessor = destinationType.addSyntheticMethod(codegenBinding, isSuperAccess());
		currentScope.problemReporter().needToEmulateMethodAccess(codegenBinding, this);
		return;

	} else if (this.binding.isProtected()){

//{ObjectTeams:
/* orig:
		SourceTypeBinding enclosingSourceType;
		if (((this.bits & ASTNode.DepthMASK) != 0)
				&& codegenBinding.declaringClass.getPackage()
					!= (enclosingSourceType = currentScope.enclosingSourceType()).getPackage()){

			SourceTypeBinding currentCompatibleType = (SourceTypeBinding)enclosingSourceType.enclosingTypeAt((this.bits & ASTNode.DepthMASK) >> ASTNode.DepthSHIFT);
  :giro */
		SourceTypeBinding enclosingSourceType = currentScope.enclosingSourceType();
		int depth = getDepthForSynthMethodAccess(this.binding, enclosingSourceType);
		if (depth > 0)
		{
			SourceTypeBinding currentCompatibleType =
				(SourceTypeBinding)enclosingSourceType.enclosingTypeAt(depth);
// SH}
			this.syntheticAccessor = currentCompatibleType.addSyntheticMethod(codegenBinding, isSuperAccess());
			currentScope.problemReporter().needToEmulateMethodAccess(codegenBinding, this);
			return;
		}
	}
//{ObjectTeams: emulate access to abstract static:
	if (TeamModel.isTeamAccessingAbstractStaticRoleMethod(currentScope.enclosingSourceType(), codegenBinding))
	{
		this.syntheticAccessor = SyntheticRoleBridgeMethodBinding.findOuterAccessor(currentScope, codegenBinding.declaringClass, codegenBinding);
		if (!currentScope.isGeneratedScope())
			currentScope.problemReporter().needToEmulateMethodAccess(codegenBinding, this);
	}
// SH}
}
@Override
public int nullStatus(FlowInfo flowInfo, FlowContext flowContext) {
	if ((this.implicitConversion & TypeIds.BOXING) != 0)
		return FlowInfo.NON_NULL;
	if (this.binding.isValidBinding()) {
		// try to retrieve null status of this message send from an annotation of the called method:
		long tagBits = this.binding.tagBits;
		if ((tagBits & TagBits.AnnotationNullMASK) == 0L) // alternatively look for type annotation (will only be present in 1.8+):
			tagBits = this.binding.returnType.tagBits & TagBits.AnnotationNullMASK;
		if(tagBits == 0L && this.binding.returnType.isFreeTypeVariable()) {
			return FlowInfo.FREE_TYPEVARIABLE;
		}
		return FlowInfo.tagBitsToNullStatus(tagBits);
	}
	return FlowInfo.UNKNOWN;
}
/**
 * @see org.eclipse.jdt.internal.compiler.ast.Expression#postConversionType(Scope)
 */
@Override
public TypeBinding postConversionType(Scope scope) {
	TypeBinding convertedType = this.resolvedType;
	if (this.valueCast != null)
		convertedType = this.valueCast;
	int runtimeType = (this.implicitConversion & TypeIds.IMPLICIT_CONVERSION_MASK) >> 4;
	switch (runtimeType) {
		case T_boolean :
			convertedType = TypeBinding.BOOLEAN;
			break;
		case T_byte :
			convertedType = TypeBinding.BYTE;
			break;
		case T_short :
			convertedType = TypeBinding.SHORT;
			break;
		case T_char :
			convertedType = TypeBinding.CHAR;
			break;
		case T_int :
			convertedType = TypeBinding.INT;
			break;
		case T_float :
			convertedType = TypeBinding.FLOAT;
			break;
		case T_long :
			convertedType = TypeBinding.LONG;
			break;
		case T_double :
			convertedType = TypeBinding.DOUBLE;
			break;
		default :
	}
	if ((this.implicitConversion & TypeIds.BOXING) != 0) {
		convertedType = scope.environment().computeBoxingType(convertedType);
	}
	return convertedType;
}

@Override
public StringBuffer printExpression(int indent, StringBuffer output){

	if (!this.receiver.isImplicitThis()) this.receiver.printExpression(0, output).append('.');
	if (this.typeArguments != null) {
		output.append('<');
		int max = this.typeArguments.length - 1;
		for (int j = 0; j < max; j++) {
			this.typeArguments[j].print(0, output);
			output.append(", ");//$NON-NLS-1$
		}
		this.typeArguments[max].print(0, output);
		output.append('>');
	}
	output.append(this.selector).append('(') ;
	if (this.arguments != null) {
		for (int i = 0; i < this.arguments.length ; i ++) {
			if (i > 0) output.append(", "); //$NON-NLS-1$
			this.arguments[i].printExpression(0, output);
		}
	}
	return output.append(')');
}

@Override
public TypeBinding resolveType(BlockScope scope) {
	// Answer the signature return type, answers PolyTypeBinding if a poly expression and there is no target type  
	// Base type promotion
	if (this.constant != Constant.NotAConstant) {
		this.constant = Constant.NotAConstant;
		long sourceLevel = scope.compilerOptions().sourceLevel;
		boolean receiverCast = false;
		if (this.receiver instanceof CastExpression) {
			this.receiver.bits |= ASTNode.DisableUnnecessaryCastCheck; // will check later on
			receiverCast = true;
		}
//{ObjectTeams: receiver may already be resolved (e.g. for _OT$cacheInitTrigger), keep that result:
	  if (this.receiver.resolvedType != null && (this.isGenerated || this instanceof RoleConstructorCall)) {
		this.actualReceiverType = this.receiver.resolvedType;
	  } else {
// orig:
		this.actualReceiverType = this.receiver.resolveType(scope);
// :giro
	  }
  // orig:
		if (this.actualReceiverType instanceof InferenceVariable) {
			scope.referenceContext().tagAsHavingIgnoredMandatoryErrors(IProblem.UndefinedMethod);
			return null; // not yet ready for resolving
		}
  /*
		this.receiverIsType = this.receiver instanceof NameReference && (((NameReference) this.receiver).bits & Binding.TYPE) != 0;
   */
    	// don't only expect NameReference, BaseReference can be static, too.
		this.receiverIsType = (   (this.receiver instanceof NameReference)
								  || (this.receiver instanceof BaseReference))
								 && (this.receiver.bits & Binding.TYPE) != 0
								 || (this.receiver instanceof TypeReference); // happens in generated AST
// MW,JH,SH}
		if (receiverCast && this.actualReceiverType != null) {
			// due to change of declaring class with receiver type, only identity cast should be notified
			TypeBinding resolvedType2 = ((CastExpression)this.receiver).expression.resolvedType;
			if (TypeBinding.equalsEquals(resolvedType2, this.actualReceiverType)) {
				if (!scope.environment().usesNullTypeAnnotations() || !NullAnnotationMatching.analyse(this.actualReceiverType, resolvedType2, -1).isAnyMismatch()) {
					scope.problemReporter().unnecessaryCast((CastExpression) this.receiver);
				}
			}
		}
		// resolve type arguments (for generic constructor call)
		if (this.typeArguments != null) {
			int length = this.typeArguments.length;
			this.argumentsHaveErrors = sourceLevel < ClassFileConstants.JDK1_5; // typeChecks all arguments
			this.genericTypeArguments = new TypeBinding[length];
			for (int i = 0; i < length; i++) {
				TypeReference typeReference = this.typeArguments[i];
				if ((this.genericTypeArguments[i] = typeReference.resolveType(scope, true /* check bounds*/, Binding.DefaultLocationTypeArgument)) == null) {
					this.argumentsHaveErrors = true;
				}
				if (this.argumentsHaveErrors && typeReference instanceof Wildcard) {
					scope.problemReporter().illegalUsageOfWildcard(typeReference);
				}
			}
			if (this.argumentsHaveErrors) {
				if (this.arguments != null) { // still attempt to resolve arguments
					for (int i = 0, max = this.arguments.length; i < max; i++) {
						this.arguments[i].resolveType(scope);
					}
				}
				return null;
			}
		}
		// will check for null after args are resolved
		if (this.arguments != null) {
			this.argumentsHaveErrors = false; // typeChecks all arguments
			int length = this.arguments.length;
			this.argumentTypes = new TypeBinding[length];
			for (int i = 0; i < length; i++){
				Expression argument = this.arguments[i];
//{ObjectTeams: keep pre-resolved binding inside generated code:
			  if (!this.isGenerated)
// SH}
				if (this.arguments[i].resolvedType != null) 
					scope.problemReporter().genericInferenceError("Argument was unexpectedly found resolved", this); //$NON-NLS-1$
				if (argument instanceof CastExpression) {
					argument.bits |= ASTNode.DisableUnnecessaryCastCheck; // will check later on
					this.argsContainCast = true;
				}
				argument.setExpressionContext(INVOCATION_CONTEXT);
//{ObjectTeams: arguments might already be resolved, see e.g. CastExpression.createRoleCheck
			  if (argument.resolvedType != null) {
				this.argumentTypes[i] = argument.resolvedType;
			  } else {
// orig:
				if ((this.argumentTypes[i] = argument.resolveType(scope)) == null){
					this.argumentsHaveErrors = true;
				}
// :giro
			  }
// SH}
			}
			if (this.argumentsHaveErrors) {
				if (this.actualReceiverType instanceof ReferenceBinding) {
					//  record a best guess, for clients who need hint about possible method match
					TypeBinding[] pseudoArgs = new TypeBinding[length];
					for (int i = length; --i >= 0;)
						pseudoArgs[i] = this.argumentTypes[i] == null ? TypeBinding.NULL : this.argumentTypes[i]; // replace args with errors with null type

					this.binding = this.receiver.isImplicitThis() ?
								scope.getImplicitMethod(this.selector, pseudoArgs, this) :
									scope.findMethod((ReferenceBinding) this.actualReceiverType, this.selector, pseudoArgs, this, false);

					if (this.binding != null && !this.binding.isValidBinding()) {
						MethodBinding closestMatch = ((ProblemMethodBinding)this.binding).closestMatch;
						// record the closest match, for clients who may still need hint about possible method match
						if (closestMatch != null) {
							if (closestMatch.original().typeVariables != Binding.NO_TYPE_VARIABLES) { // generic method
								// shouldn't return generic method outside its context, rather convert it to raw method (175409)
								closestMatch = scope.environment().createParameterizedGenericMethod(closestMatch.original(), (RawTypeBinding)null);
							}
							this.binding = closestMatch;
							MethodBinding closestMatchOriginal = closestMatch.original();
							if (closestMatchOriginal.isOrEnclosedByPrivateType() && !scope.isDefinedInMethod(closestMatchOriginal)) {
								// ignore cases where method is used from within inside itself (e.g. direct recursions)
								closestMatchOriginal.modifiers |= ExtraCompilerModifiers.AccLocallyUsed;
							}
						}
					}
//{ObjectTeams: more tagging as used:
					else if (this.binding != null && this.binding.isOrEnclosedByPrivateType() && !scope.isDefinedInMethod(this.binding)) {
						// ignore cases where method is used from within inside itself (e.g. direct recursions)
						this.binding.modifiers |= ExtraCompilerModifiers.AccLocallyUsed;
					}
// SH}
				}
				return null;
			}
		}
		if (this.actualReceiverType == null) {
			return null;
		}
		// base type cannot receive any message
		if (this.actualReceiverType.isBaseType()) {
			scope.problemReporter().errorNoMethodFor(this, this.actualReceiverType, this.argumentTypes);
			return null;
		}
	}
	
//{ObjectTeams: revert receiver weakening & setup anchorMapping:
	if (this.actualReceiverType instanceof WeakenedTypeBinding) {
		// this happens if receiver is a role-type field from a super team, see B.1.1-otjld-sh-32.
		this.actualReceiverType = ((WeakenedTypeBinding)this.actualReceiverType).getStrongType();
		this.bits |= NeedReceiverGenericCast; // addes a cast to this.actualReceiverType;
	}
	if (this.receiver.isThis() && !receiverIsQualifiedThis() && this.actualReceiverType.isRole()) {
		this.actualReceiverType = ((ReferenceBinding)this.actualReceiverType).getRealClass();
		if (this.actualReceiverType == null) // happens with true role ifc
			this.actualReceiverType = ((ReferenceBinding)this.receiver.resolvedType).getRealType();
	}
	AnchorMapping anchorMapping = null;
	int baseTypeModifiers = -1;
	ReferenceBinding receiverReferenceType = (this.actualReceiverType instanceof ReferenceBinding) ?
												(ReferenceBinding)this.actualReceiverType :
												null;
    // forward declaration of original local used below:
    TypeBinding returnType = null;
  try {
	anchorMapping = beforeMethodLookup(this.argumentTypes, scope);
	if (isDecapsulationAllowed(scope) && receiverReferenceType != null) {
	    // for now pretend all base classes are public/visible (avoid ReceiverTypeNotVisible)
		baseTypeModifiers = receiverReferenceType.modifiers;
    	receiverReferenceType.modifiers &= ~ExtraCompilerModifiers.AccVisibilityMASK;
    	receiverReferenceType.modifiers |= ClassFileConstants.AccPublic;
	}
// SH}
//{ObjectTeams: for calls to lower() resolve using internal selector _OT$getBase:
	char[] realSelector = this.selector;
	if (   CharOperation.equals(this.selector, IOTConstants.LOWER)
		&& this.actualReceiverType.isRole()
		&& this.arguments == null
		&& this.actualReceiverType.isCompatibleWith(scope.getType(
				new char[][] {IOTConstants.ORG, IOTConstants.OBJECTTEAMS, IOTConstants.ITEAM, IOTConstants.ILOWERABLE},
				4)))
		this.selector=IOTConstants._OT_GETBASE;
// orig:

	TypeBinding methodType = findMethodBinding(scope);
	if (methodType != null && methodType.isPolyType()) {
		this.resolvedType = this.binding.returnType.capture(scope, this.sourceStart, this.sourceEnd);
		return methodType;
	}

// :giro
	if (this.binding == null)
		return null; // severe error, don't proceed analysing
	this.selector = realSelector;
// SH}
//{ObjectTeams: postprocessing
    // wrap return type with knowledge of anchor mapping
	returnType = RoleTypeCreator.maybeWrapQualifiedRoleType(this, scope);
  } finally {
	// cleanup (required)
    AnchorMapping.removeCurrentMapping(anchorMapping);
	if (baseTypeModifiers != -1)
	   	receiverReferenceType.modifiers = baseTypeModifiers; // not-null if baseTypeModifiers != -1
  }
  // more checks requiring the binding to be known (possibly updating returnType):
  returnType = afterMethodLookup(scope, anchorMapping, this.argumentTypes, returnType);
// SH}
//{ObjectTeams illegal call to callin? (do this before reporting mismatching args - where callin args are in fact enhanced!)
	int bindingModifiers = this.binding.modifiers;
	if (!this.binding.isValidBinding()) {
		ProblemMethodBinding problem = (ProblemMethodBinding)this.binding;
		if (problem.closestMatch != null)
			bindingModifiers = problem.closestMatch.modifiers;
	}
	// can we infer a callout to implement the missing method?
	if (this.binding.problemId() == ProblemReasons.NotFound) {
		if (CharOperation.prefixEquals(IOTConstants.OT_DOLLAR_NAME, this.selector) && scope.referenceType().ignoreFurtherInvestigation)
			return null; // type already reported error, this message send is obviously generated, don't bother any more
		if (CalloutImplementor.inferMappingFromCall(scope.referenceType(), this, this.argumentTypes)) {
			scope.problemReporter().usingInferredCalloutForMessageSend(this);
			returnType = this.binding.returnType;
			bindingModifiers = this.binding.modifiers;
		}
	} else {
		CalloutMappingDeclaration callout = MethodModel.getImplementingInferredCallout(this.binding); // reusing previously inferred callout?
		if (callout != null) {
			if (callout.isCalloutToField())
				scope.problemReporter().usingInferredCalloutToFieldForMessageSend(this); // error
			else
				scope.problemReporter().usingInferredCalloutForMessageSend(this);		 // warning
		}
	}
	if ((bindingModifiers & ExtraCompilerModifiers.AccCallin) != 0) {
		AbstractMethodDeclaration enclosingMethod = scope.methodScope().referenceMethod();
		boolean callinAllowed;
		if (enclosingMethod == null)
			callinAllowed = false;
		else if (enclosingMethod instanceof MethodDeclaration && ((MethodDeclaration)enclosingMethod).isMappingWrapper == WrapperKind.CALLIN)
			callinAllowed = true;
		else if (isAnySuperAccess())
			callinAllowed = ((enclosingMethod.modifiers & ExtraCompilerModifiers.AccCallin) != 0);
		else
			callinAllowed = enclosingMethod.isGenerated;
		if (!callinAllowed) {
			// Note that the problem reporter wrapper of a base call ignores this error:
			scope.problemReporter().callToCallin(this.binding, this);
			return this.resolvedType = null;
		}
	}
	WeavingScheme weavingScheme = scope.compilerOptions().weavingScheme;
// SH}

	if (!this.binding.isValidBinding()) {
		if (this.binding.declaringClass == null) {
			if (this.actualReceiverType instanceof ReferenceBinding) {
				this.binding.declaringClass = (ReferenceBinding) this.actualReceiverType;
			} else {
				scope.problemReporter().errorNoMethodFor(this, this.actualReceiverType, this.argumentTypes);
				return null;
			}
		}
//{ObjectTeams: Decapsulation:
		if(   this.binding.problemId() == ProblemReasons.NotVisible
		   && isDecapsulationAllowed(scope))
		{
			this.binding = ((ProblemMethodBinding)this.binding).closestMatch;
			if (!this.binding.declaringClass.isRole()) { // access via interface is possible anyway, no access wrapper needed.
				// need an accessor method that will be generated by the OTRE / OTREDyn
				if (this.accessId == -1) // -1 happens for BaseAllocationExpression with role-as-base
					this.accessId = scope.enclosingSourceType().roleModel.addInaccessibleBaseMethod(this.binding);
				// pretend that accessor method were already there:
				if (this.accessId == 0)
					this.accessId = scope.enclosingSourceType().roleModel.addInaccessibleBaseMethod(this.binding);
				if (weavingScheme == WeavingScheme.OTDRE) {
					MethodBinding accessor = CalloutImplementorDyn.ensureAccessor(scope, this.binding.declaringClass, this.binding.isStatic());
					OTDREMethodDecapsulation updatableAccessor = new OTDREMethodDecapsulation(accessor, this.binding.parameters, this.binding.returnType, this.accessId, scope, this);
					ReferenceBinding enclosingType = scope.enclosingSourceType().enclosingType();
					if (enclosingType != null && enclosingType.isTeam())
						enclosingType.getTeamModel().recordUpdatableAccessId(updatableAccessor);
					this.syntheticAccessor = updatableAccessor;
				} else {
					this.binding = new MethodBinding(this.binding, this.binding.declaringClass.getRealClass());
					this.binding.selector = CharOperation.concat(IOTConstants.OT_DECAPS, this.selector);
				}
			}
			this.isDecapsulation = true;
			scope.problemReporter().decapsulation(this, scope);
			try {
				anchorMapping = AnchorMapping.setupNewMapping(receiverForAnchorMapping(scope), this.arguments, scope);
				returnType = RoleTypeCreator.maybeWrapQualifiedRoleType(this, scope); // update
			} finally {
				AnchorMapping.removeCurrentMapping(anchorMapping);
			}
		} else {
// orig:
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=245007 avoid secondary errors in case of
		// missing super type for anonymous classes ... 
		ReferenceBinding declaringClass = this.binding.declaringClass;
		boolean avoidSecondary = declaringClass != null &&
								 declaringClass.isAnonymousType() &&
								 declaringClass.superclass() instanceof MissingTypeBinding;
		if (!avoidSecondary)
			scope.problemReporter().invalidMethod(this, this.binding, scope);
		MethodBinding closestMatch = ((ProblemMethodBinding)this.binding).closestMatch;
		switch (this.binding.problemId()) {
			case ProblemReasons.Ambiguous :
				break; // no resilience on ambiguous
			case ProblemReasons.InferredApplicableMethodInapplicable:
			case ProblemReasons.InvocationTypeInferenceFailure:
				// Grabbing the closest match improves error reporting in nested invocation contexts
				if (this.expressionContext != INVOCATION_CONTEXT)
					break;
				//$FALL-THROUGH$
			case ProblemReasons.NotVisible :
			case ProblemReasons.NonStaticReferenceInConstructorInvocation :
			case ProblemReasons.NonStaticReferenceInStaticContext :
			case ProblemReasons.ReceiverTypeNotVisible :
			case ProblemReasons.ParameterBoundMismatch :
				// only steal returnType in cases listed above
				if (closestMatch != null) this.resolvedType = closestMatch.returnType;
				break;
			case ProblemReasons.ContradictoryNullAnnotations :
				if (closestMatch != null && closestMatch.returnType != null)
					this.resolvedType = closestMatch.returnType.withoutToplevelNullAnnotation();
				break;
		}
		// record the closest match, for clients who may still need hint about possible method match
		if (closestMatch != null) {
			this.binding = closestMatch;
			MethodBinding closestMatchOriginal = closestMatch.original();
			if (closestMatchOriginal.isOrEnclosedByPrivateType() && !scope.isDefinedInMethod(closestMatchOriginal)) {
				// ignore cases where method is used from within inside itself (e.g. direct recursions)
				closestMatchOriginal.modifiers |= ExtraCompilerModifiers.AccLocallyUsed;
			}
		}
		return (this.resolvedType != null && (this.resolvedType.tagBits & TagBits.HasMissingType) == 0)
						? this.resolvedType
						: null;
// :giro
		}
// SH}
	}
//{ObjectTeams: new check (base.m() within m() ?)
    if (this.receiver instanceof BaseReference)
    {
    	if (weavingScheme == WeavingScheme.OTRE) {
	        AbstractMethodDeclaration enclosingMethod = BaseCallMessageSend.findEnclosingCallinMethod(scope, null);
	        if (enclosingMethod != null) {
		        MethodBinding basecallSurrogate = null;
		        if (enclosingMethod.model != null)
		        	basecallSurrogate = enclosingMethod.model.getBaseCallSurrogate();
		        if (this.binding != basecallSurrogate)
		        	scope.problemReporter().baseCallNotSameMethod(enclosingMethod, this);
	        }
    	}
    }
// SH}
	final CompilerOptions compilerOptions = scope.compilerOptions();
	if (compilerOptions.complianceLevel <= ClassFileConstants.JDK1_6
			&& this.binding.isPolymorphic()) {
		scope.problemReporter().polymorphicMethodNotBelow17(this);
		return null;
	}

	if (compilerOptions.isAnnotationBasedNullAnalysisEnabled) {
		ImplicitNullAnnotationVerifier.ensureNullnessIsKnown(this.binding, scope);
		if (compilerOptions.sourceLevel >= ClassFileConstants.JDK1_8) {
			if (this.binding instanceof ParameterizedGenericMethodBinding && this.typeArguments != null) {
				TypeVariableBinding[] typeVariables = this.binding.original().typeVariables();
				for (int i = 0; i < this.typeArguments.length; i++)
					this.typeArguments[i].checkNullConstraints(scope, (ParameterizedGenericMethodBinding) this.binding, typeVariables, i);
			}
		}
//{ObjectTeams: integrate @NNBD with role type wrapping above
		boolean oldIsNonNull = (returnType.tagBits & TagBits.AnnotationNonNull) != 0;
		boolean newIsNonNull = (this.binding.returnType.tagBits & TagBits.AnnotationNonNull) != 0;
		if (!oldIsNonNull && newIsNonNull)
			returnType = scope.environment().createAnnotatedType(returnType, new AnnotationBinding[] {scope.environment().getNonNullAnnotation()});
// SH}
	}
	
	if (((this.bits & ASTNode.InsideExpressionStatement) != 0)
			&& this.binding.isPolymorphic()) {
		// we only set the return type to be void if this method invocation is used inside an expression statement
		this.binding = scope.environment().updatePolymorphicMethodReturnType((PolymorphicMethodBinding) this.binding, TypeBinding.VOID);
	}
	if ((this.binding.tagBits & TagBits.HasMissingType) != 0) {
		scope.problemReporter().missingTypeInMethod(this, this.binding);
	}
	if (!this.binding.isStatic()) {
		// the "receiver" must not be a type
		if (this.receiverIsType) {
			scope.problemReporter().mustUseAStaticMethod(this, this.binding);
			if (this.actualReceiverType.isRawType()
					&& (this.receiver.bits & ASTNode.IgnoreRawTypeCheck) == 0
					&& compilerOptions.getSeverity(CompilerOptions.RawTypeReference) != ProblemSeverities.Ignore) {
				scope.problemReporter().rawTypeReference(this.receiver, this.actualReceiverType);
			}
		} else {
			// handle indirect inheritance thru variable secondary bound
			// receiver may receive generic cast, as part of implicit conversion
			TypeBinding oldReceiverType = this.actualReceiverType;
			this.actualReceiverType = this.actualReceiverType.getErasureCompatibleType(this.binding.declaringClass);
			this.receiver.computeConversion(scope, this.actualReceiverType, this.actualReceiverType);
			if (TypeBinding.notEquals(this.actualReceiverType, oldReceiverType) && TypeBinding.notEquals(this.receiver.postConversionType(scope), this.actualReceiverType)) { // record need for explicit cast at codegen since receiver could not handle it
				this.bits |= NeedReceiverGenericCast;
			}
		}
	} else {
		// static message invoked through receiver? legal but unoptimal (optional warning).
		if (this.binding.declaringClass.isInterface() && !((isTypeAccess() || this.receiver.isImplicitThis()) && TypeBinding.equalsEquals(this.binding.declaringClass, this.actualReceiverType))) {
//{ObjectTeams: static in role-ifc is OK
		  if (!this.binding.declaringClass.isSynthInterface())
// SH}
			scope.problemReporter().nonStaticOrAlienTypeReceiver(this, this.binding);
		} else if (!(this.receiver.isImplicitThis() || this.receiver.isSuper() || this.receiverIsType)) {
		 	scope.problemReporter().nonStaticAccessToStaticMethod(this, this.binding);
		}
		if (!this.receiver.isImplicitThis() && TypeBinding.notEquals(this.binding.declaringClass, this.actualReceiverType)) {
			scope.problemReporter().indirectAccessToStaticMethod(this, this.binding);
		}
	}
	if (checkInvocationArguments(scope, this.receiver, this.actualReceiverType, this.binding, this.arguments, this.argumentTypes, this.argsContainCast, this)) {
		this.bits |= ASTNode.Unchecked;
	}

	//-------message send that are known to fail at compile time-----------
	if (this.binding.isAbstract()) {
//{ObjectTeams: also treat tsuper as super:
/* orig:
		if (this.receiver.isSuper()) {
  :giro */
		if (isAnySuperAccess()) {
// SH}
			scope.problemReporter().cannotDireclyInvokeAbstractMethod(this, this.binding);
		}
		// abstract private methods cannot occur nor abstract static............
	}
//{ObjectTeams: use copy inheritance src if possible (scope analysis and error reporting):
/* orig:
	if (isMethodUseDeprecated(this.binding, scope, true))
		scope.problemReporter().deprecatedMethod(this.binding, this);
  :giro */
  {
	MethodBinding origMethod = this.binding.copyInheritanceSrc != null ? this.binding.copyInheritanceSrc : this.binding;
	if (isMethodUseDeprecated(origMethod, scope, true))
		scope.problemReporter().deprecatedMethod(origMethod, this);
  }
// SH}

//{ObjectTeams: forbidden creation method?
	if (   this.binding.model != null
			&& this.binding.model.isForbiddenCreationMethod())
	{
		scope.problemReporter().abstractRoleIsRelevant(
				this, (ReferenceBinding)this.binding.returnType);
	}
// SH}
//{ObjectTeams: use (adjusted) type from above and further adjust
/* orig:
	TypeBinding returnType;
 */
	if ((this.bits & ASTNode.Unchecked) != 0 && this.genericTypeArguments == null) {
		// TODO(SH): 3.5: should this branch be affected by the checks below, too?
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=277643, align with javac on JLS 15.12.2.6
		returnType = this.binding.returnType;
		if (returnType != null) {
			returnType = scope.environment().convertToRawType(returnType.erasure(), true);
		}
	} else {
/*
		returnType = this.binding.returnType;
 :giro */
		// check role-type return type in non-generated methods:
		if (  !scope.isGeneratedScope()
			&& returnType instanceof ReferenceBinding)
		{
			if (StandardElementGenerator.isCastToMethod(this.selector)) {
				this.resolvedType = this.binding.returnType; // pre-set to avoid wrapping this type // FIXME(SH): redundant?!
			} else {
				// signature weakening might have produced the wrong returnType.
				// check if we must cast this expression to the strengthened version:
				returnType = checkStrengthenReturnType(returnType, scope);
			}
		}
// orig:
		if (returnType != null) {
			returnType = returnType.capture(scope, this.sourceStart, this.sourceEnd);
		}
// :giro
	}
// SH}
	this.resolvedType = returnType;
	if (this.receiver.isSuper() && compilerOptions.getSeverity(CompilerOptions.OverridingMethodWithoutSuperInvocation) != ProblemSeverities.Ignore) {
		final ReferenceContext referenceContext = scope.methodScope().referenceContext;
		if (referenceContext instanceof AbstractMethodDeclaration) {
			final AbstractMethodDeclaration abstractMethodDeclaration = (AbstractMethodDeclaration) referenceContext;
			MethodBinding enclosingMethodBinding = abstractMethodDeclaration.binding;
			if (enclosingMethodBinding.isOverriding()
					&& CharOperation.equals(this.binding.selector, enclosingMethodBinding.selector)
					&& this.binding.areParametersEqual(enclosingMethodBinding)) {
				abstractMethodDeclaration.bits |= ASTNode.OverridingMethodWithSupercall;
			}
		}
	}
	if (this.receiver.isSuper() && this.actualReceiverType.isInterface()) {
		// 15.12.3 (Java 8)
		scope.checkAppropriateMethodAgainstSupers(this.selector, this.binding, this.argumentTypes, this);
	}
	if (this.typeArguments != null && this.binding.original().typeVariables == Binding.NO_TYPE_VARIABLES) {
		scope.problemReporter().unnecessaryTypeArgumentsForMethodInvocation(this.binding, this.genericTypeArguments, this.typeArguments);
	}
	return (this.resolvedType.tagBits & TagBits.HasMissingType) == 0
				? this.resolvedType
				: null;
}

protected TypeBinding findMethodBinding(BlockScope scope) {
	ReferenceContext referenceContext = scope.methodScope().referenceContext;
	if (referenceContext instanceof LambdaExpression) {
		this.outerInferenceContext = ((LambdaExpression) referenceContext).inferenceContext;
	}
	
	if (this.expectedType != null && this.binding instanceof PolyParameterizedGenericMethodBinding) {
		this.binding = this.solutionsPerTargetType.get(this.expectedType);
	}
	if (this.binding == null) { // first look up or a "cache miss" somehow.
		this.binding = this.receiver.isImplicitThis() ? 
				scope.getImplicitMethod(this.selector, this.argumentTypes, this) 
				: scope.getMethod(this.actualReceiverType, this.selector, this.argumentTypes, this);

	    if (this.binding instanceof PolyParameterizedGenericMethodBinding) {
		    this.solutionsPerTargetType = new HashMap<TypeBinding, MethodBinding>();
		    return new PolyTypeBinding(this);
	    }
	}
	resolvePolyExpressionArguments(this, this.binding, this.argumentTypes, scope);
	return this.binding.returnType;
}


//{ObjectTeams: utils:
private boolean receiverIsQualifiedThis() {
	if (this.receiver instanceof QualifiedThisReference)
		return true;
	if (this.receiver instanceof BaseReference)
		return (((BaseReference)this.receiver).isQualified());
	return false;
}
public boolean isDecapsulationAllowed(Scope scope) {
	MethodScope methodScope = scope.methodScope();
	// Note: methodScope() may return initializerScope, which has no Method, but the Type as refcontext.
	if (methodScope != null && methodScope.referenceContext instanceof AbstractMethodDeclaration) {
		AbstractMethodDeclaration method = (AbstractMethodDeclaration) methodScope.referenceContext;
		return method.isCalloutWrapper() || method.isBasePredicate();
	}
	return false;
}
// SH}
//{ObjectTeams: Hooks around method lookup (using getMethod/getImplicitMethod):
protected AnchorMapping beforeMethodLookup(
		TypeBinding[] argumentTypes, Scope scope)
{
	AnchorMapping result = null;
	try {
		if (this.actualReceiverType instanceof ReferenceBinding) // funny thing: receiver could be array..
			Dependencies.ensureBindingState(
	            (ReferenceBinding)this.actualReceiverType,
	            ITranslationStates.STATE_METHODS_CREATED); // creates bindings for possible target methods (incl. shorthand callout) 
	} finally {
		// it is essential that we setup an anchor mapping in any case.
		result = AnchorMapping.setupNewMapping(receiverForAnchorMapping(scope), this.arguments, scope);
	}
	return result;
}
/* The role method call in a callin wrapper should not set the receiver,
 * because it appears in the role context, but needs an explicit role receiver.
 */
private Expression receiverForAnchorMapping(Scope scope) {
	MethodScope methodScope = scope.methodScope();
	if (methodScope != null && methodScope.isCallinWrapper() && this.isPushedOutRoleMethodCall)
    	return null; // pretend the call target is already "this" = the role.

	return this.receiver;
}

protected TypeBinding afterMethodLookup(Scope scope, AnchorMapping anchorMapping, TypeBinding[] argumentTypes, TypeBinding returnType)
{
	// tweak methods of predefined confined types:
	if (   this.binding.isValidBinding()
		&& CharOperation.equals(this.binding.declaringClass.compoundName, IOTConstants.ORG_OBJECTTEAMS_TEAM_OTCONFINED)
		&& !CharOperation.equals(this.selector, IOTConstants._OT_GETTEAM))
	{
		// methods found in a predefined confined type are actually methods of Object
		// (except for _OT$getTeam() which is generated for each role):
		ReferenceBinding object = scope.getJavaLangObject();
		this.binding = scope.findMethod(object, this.binding.selector, this.binding.parameters, this, false);
	}

	// check whether all anchors in the signature are final
	// (this step was skipped during instantiate parameters).
	if (this.binding.isValidBinding() && anchorMapping != null)
		if (!anchorMapping.checkInstantiatedParameters(this, scope)) {
			// store a problem binding with the instantiated arguments for further error reporting:
			MethodBinding instantiatedMethod = new MethodBinding(this.binding, (ReferenceBinding)this.actualReceiverType);
			instantiatedMethod.parameters = anchorMapping.getInstantiatedParameters(this.binding);
			this.binding = new ProblemMethodBinding(instantiatedMethod, this.selector, this.binding.parameters, ProblemReasons.NotFound);
		}

	// check access to static role method via the interface
	if (this.binding.isStatic() && this.actualReceiverType.isInterface()) {
		// static method in an interface can only occur in a role.
		ReferenceBinding receiverType = (ReferenceBinding)this.actualReceiverType;
		if (receiverType.isRole()) {
			ReferenceBinding classPart = receiverType.roleModel.getClassPartBinding();
			this.actualReceiverType = classPart; // used when calling getUpdatedMethodBinding() and during codeGen
		}
	}

	// check role-type return type in non-generated methods:
	if (  !scope.isGeneratedScope()
		&& this.binding.returnType instanceof ReferenceBinding)
	{
		if (StandardElementGenerator.isCastToMethod(this.selector)) {
			this.resolvedType = this.binding.returnType; // pre-set to avoid wrapping this type
		}
	}
	return returnType; // not changed
}
/** Is this message send legally referring to a role's baseclass? */
@Override
public DecapsulationState getBaseclassDecapsulation() {
	if (CharOperation.equals(this.selector, IOTConstants._OT_GETBASE))
//		return DecapsulationState.REPORTED;
//	if (CopyInheritance.isCreator(this.selector))
		return DecapsulationState.REPORTED;
	return this.receiver.getBaseclassDecapsulation();
}
/* if a cast for the return value is needed (due to weakening) perform necessary changes
 * on resolvedType and valueCast, return the strengthenedType. */
private TypeBinding checkStrengthenReturnType(TypeBinding returnType, Scope scope)
{
	ReferenceBinding currentType = (ReferenceBinding) returnType.leafComponentType();
	if (currentType.isTypeVariable()) {
		TypeBinding typeBound= ((TypeVariableBinding)currentType).firstBound;
		if (typeBound instanceof ReferenceBinding)
			currentType= (ReferenceBinding)typeBound;
	}
	if (!currentType.isRole())
		return returnType; // unchanged

	ReferenceBinding strengthenedReturnType = null;

	if (currentType instanceof WeakenedTypeBinding) {
		WeakenedTypeBinding weakenedTypeBinding = (WeakenedTypeBinding)currentType;
		if (!weakenedTypeBinding.isSignificantlyWeakened())
			return returnType; // unchanged

		strengthenedReturnType = weakenedTypeBinding.getStrongType();

	} else {
		ReferenceBinding site = (ReferenceBinding)this.actualReceiverType;
		if (this.receiver.isSuper())
			site = scope.enclosingSourceType();
		strengthenedReturnType = (ReferenceBinding)TeamModel.strengthenRoleType(site, currentType);
		if (TypeBinding.equalsEquals(strengthenedReturnType, currentType))
			return returnType; //unchanged
		
		// if strengthenRoleType actually worked it returned a dependent type
		this.resolvedType = WeakenedTypeBinding.makeWeakenedTypeBinding((DependentTypeBinding) strengthenedReturnType, currentType, returnType.dimensions());
	}
	// schedule for generating a cast
	this.valueCast = strengthenedReturnType;
	return strengthenedReturnType; // this is what clients should see
}
// SH}
//{ObjectTeams: references to the enclosing team need synthetic accessors.
//              see also: Reference.getDepthForSynthFieldAccess(..)
/**
 * @param methodBinding
 * @param enclosingSourceType
 * @return depth of field's declaring class as seen from enclosingSourceTypes or -1.
 */
protected int getDepthForSynthMethodAccess(MethodBinding methodBinding, SourceTypeBinding enclosingSourceType)
{
	int depth = (this.bits & ASTNode.DepthMASK) >> ASTNode.DepthSHIFT;

	if (methodBinding.isPrivate())
		return depth;
	if (!methodBinding.isProtected())
		return -1;
	if (methodBinding.declaringClass.getPackage() == enclosingSourceType.getPackage()) {
		depth = TeamModel.levelFromEnclosingTeam(methodBinding.declaringClass, enclosingSourceType);
		// through copy inheritance this code could be executed within a different package!
		if (depth == 0)
			return -1; // neither a team field, nor an access across packages
	}
	return depth;
}
// SH}

@Override
public void setActualReceiverType(ReferenceBinding receiverType) {
	if (receiverType == null) return; // error scenario only
	this.actualReceiverType = receiverType;
}
@Override
public void setDepth(int depth) {
	this.bits &= ~ASTNode.DepthMASK; // flush previous depth if any
	if (depth > 0) {
		this.bits |= (depth & 0xFF) << ASTNode.DepthSHIFT; // encoded on 8 bits
	}
}

/**
 * @see org.eclipse.jdt.internal.compiler.ast.Expression#setExpectedType(org.eclipse.jdt.internal.compiler.lookup.TypeBinding)
 */
@Override
public void setExpectedType(TypeBinding expectedType) {
    this.expectedType = expectedType;
}

@Override
public void setExpressionContext(ExpressionContext context) {
	this.expressionContext = context;
}

@Override
public boolean isPolyExpression() {
	
	/* 15.12 has four requirements: 1) The invocation appears in an assignment context or an invocation context
       2) The invocation elides NonWildTypeArguments 3) the method to be invoked is a generic method (8.4.4).
       4) The return type of the method to be invoked mentions at least one of the method's type parameters.

       We are in no position to ascertain the last two until after resolution has happened. So no client should
       depend on asking this question before resolution.
	 */
	return isPolyExpression(this.binding);
}

@Override
public boolean isBoxingCompatibleWith(TypeBinding targetType, Scope scope) {
	if (this.argumentsHaveErrors || this.binding == null || !this.binding.isValidBinding() || targetType == null || scope == null)
		return false;
	if (isPolyExpression() && !targetType.isPrimitiveOrBoxedPrimitiveType()) // i.e it is dumb to trigger inference, checking boxing compatibility against say Collector<? super T, A, R>.
		return false;
	TypeBinding originalExpectedType = this.expectedType;
	try {
		MethodBinding method = this.solutionsPerTargetType != null ? this.solutionsPerTargetType.get(targetType) : null;
		if (method == null) {
			this.expectedType = targetType;
			// No need to tunnel through overload resolution. this.binding is the MSMB.
			method = isPolyExpression() ? ParameterizedGenericMethodBinding.computeCompatibleMethod18(this.binding.shallowOriginal(), this.argumentTypes, scope, this) : this.binding;
			registerResult(targetType, method);
		}
		if (method == null || !method.isValidBinding() || method.returnType == null || !method.returnType.isValidBinding())
			return false;
		return super.isBoxingCompatible(method.returnType.capture(scope, this.sourceStart, this.sourceEnd), targetType, this, scope);
	} finally {
		this.expectedType = originalExpectedType;
	}
}

@Override
public boolean isCompatibleWith(TypeBinding targetType, final Scope scope) {
	if (this.argumentsHaveErrors || this.binding == null || !this.binding.isValidBinding() || targetType == null || scope == null)
		return false;
	TypeBinding originalExpectedType = this.expectedType;
	try {
		MethodBinding method = this.solutionsPerTargetType != null ? this.solutionsPerTargetType.get(targetType) : null;
		if (method == null) {
			this.expectedType = targetType;
			// No need to tunnel through overload resolution. this.binding is the MSMB.
			method = isPolyExpression() ? ParameterizedGenericMethodBinding.computeCompatibleMethod18(this.binding.shallowOriginal(), this.argumentTypes, scope, this) : this.binding;
			registerResult(targetType, method);
		}
		TypeBinding returnType;
//{ObjectTeams:
		if (this.resolvedType != null && this.resolvedType.isValidBinding() && !this.resolvedType.isPolyType() && this.resolvedType.isProperType(false))
			if (this.resolvedType.isCompatibleWith(targetType, scope))
				return true;
// SH}
		if (method == null || !method.isValidBinding() || (returnType = method.returnType) == null || !returnType.isValidBinding())
			return false;
		if ((this.bits & ASTNode.Unchecked) != 0 && this.genericTypeArguments == null)
			returnType = scope.environment().convertToRawType(returnType.erasure(), true);
		return returnType.capture(scope, this.sourceStart, this.sourceEnd).isCompatibleWith(targetType, scope);
	} finally {
		this.expectedType = originalExpectedType;
	}
}

/** Variant of isPolyExpression() to be used during type inference, when a resolution candidate exists. */
@Override
public boolean isPolyExpression(MethodBinding resolutionCandidate) {
	if (this.expressionContext != ASSIGNMENT_CONTEXT && this.expressionContext != INVOCATION_CONTEXT)
		return false;
	
	if (this.typeArguments != null && this.typeArguments.length > 0)
		return false;
	
	if (this.constant != Constant.NotAConstant)
		throw new UnsupportedOperationException("Unresolved MessageSend can't be queried if it is a polyexpression"); //$NON-NLS-1$
	
	if (resolutionCandidate != null) {
		if (resolutionCandidate instanceof ParameterizedGenericMethodBinding) {
			ParameterizedGenericMethodBinding pgmb = (ParameterizedGenericMethodBinding) resolutionCandidate;
			if (pgmb.inferredReturnType)
				return true; // if already determined
		} 
		if (resolutionCandidate.returnType != null) {
			// resolution may have prematurely instantiated the generic method, we need the original, though:
			MethodBinding candidateOriginal = resolutionCandidate.original();
			return candidateOriginal.returnType.mentionsAny(candidateOriginal.typeVariables(), -1);
		}
	}
	
	return false;
}

@Override
public boolean sIsMoreSpecific(TypeBinding s, TypeBinding t, Scope scope) {
	if (super.sIsMoreSpecific(s, t, scope))
		return true;
	return isPolyExpression() ? !s.isBaseType() && t.isBaseType() : false;
}

@Override
public void setFieldIndex(int depth) {
	// ignore for here
}
@Override
public TypeBinding invocationTargetType() {
	return this.expectedType;
}

@Override
public void traverse(ASTVisitor visitor, BlockScope blockScope) {
	if (visitor.visit(this, blockScope)) {
		this.receiver.traverse(visitor, blockScope);
		if (this.typeArguments != null) {
			for (int i = 0, typeArgumentsLength = this.typeArguments.length; i < typeArgumentsLength; i++) {
				this.typeArguments[i].traverse(visitor, blockScope);
			}
		}
		if (this.arguments != null) {
			int argumentsLength = this.arguments.length;
			for (int i = 0; i < argumentsLength; i++)
				this.arguments[i].traverse(visitor, blockScope);
		}
	}
	visitor.endVisit(this, blockScope);
}
@Override
public boolean statementExpression() {
	return ((this.bits & ASTNode.ParenthesizedMASK) == 0);
}
@Override
public boolean receiverIsImplicitThis() {
	return this.receiver.isImplicitThis();
}
// -- interface Invocation: --
@Override
public MethodBinding binding() {
	return this.binding;
}

@Override
public void registerInferenceContext(ParameterizedGenericMethodBinding method, InferenceContext18 infCtx18) {
	if (this.inferenceContexts == null)
		this.inferenceContexts = new SimpleLookupTable();
	this.inferenceContexts.put(method, infCtx18);
}

@Override
public void registerResult(TypeBinding targetType, MethodBinding method) {
	if (this.solutionsPerTargetType == null)
		this.solutionsPerTargetType = new HashMap<TypeBinding, MethodBinding>();
	this.solutionsPerTargetType.put(targetType, method);
}

@Override
public InferenceContext18 getInferenceContext(ParameterizedMethodBinding method) {
	if (this.inferenceContexts == null)
		return null;
	return (InferenceContext18) this.inferenceContexts.get(method);
}
@Override
public void cleanUpInferenceContexts() {
	if (this.inferenceContexts == null)
		return;
	for (Object value : this.inferenceContexts.valueTable)
		if (value != null)
			((InferenceContext18) value).cleanUp();
	this.inferenceContexts = null;
	this.outerInferenceContext = null;
	this.solutionsPerTargetType = null;
}
@Override
public Expression[] arguments() {
	return this.arguments;
}
@Override
public ExpressionContext getExpressionContext() {
	return this.expressionContext;
}
// -- Interface InvocationSite: --
@Override
public InferenceContext18 freshInferenceContext(Scope scope) {
	return new InferenceContext18(scope, this.arguments, this, this.outerInferenceContext);
}
@Override
public boolean isQualifiedSuper() {
	return this.receiver.isQualifiedSuper();
}
}
