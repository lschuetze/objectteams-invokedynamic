/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2003, 2018 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.mappings;

import static org.eclipse.objectteams.otdt.core.compiler.ISMAPConstants.STEP_OVER_LINENUMBER;
import static org.eclipse.objectteams.otdt.core.compiler.ISMAPConstants.STEP_OVER_SOURCEPOSITION_END;
import static org.eclipse.objectteams.otdt.core.compiler.ISMAPConstants.STEP_OVER_SOURCEPOSITION_START;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration.WrapperKind;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ArrayReference;
import org.eclipse.jdt.internal.compiler.ast.CastExpression;
import org.eclipse.jdt.internal.compiler.ast.EqualExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.Expression.DecapsulationState;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.OperatorIds;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.BaseTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.parser.TerminalTokens;
import org.eclipse.jdt.internal.compiler.util.Messages;
import org.eclipse.objectteams.otdt.core.compiler.IOTConstants;
import org.eclipse.objectteams.otdt.core.compiler.Pair;
import org.eclipse.objectteams.otdt.core.exceptions.InternalCompilerError;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.AbstractMethodMappingDeclaration;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.CallinMappingDeclaration;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.MethodSpec;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PotentialLiftExpression;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PotentialLowerExpression;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PotentialRoleReceiverExpression;
import org.eclipse.objectteams.otdt.internal.core.compiler.bytecode.CallinMethodMappingsAttribute;
import org.eclipse.objectteams.otdt.internal.core.compiler.bytecode.CallinParamMappingsAttribute;
import org.eclipse.objectteams.otdt.internal.core.compiler.bytecode.CopyInheritanceSourceAttribute;
import org.eclipse.objectteams.otdt.internal.core.compiler.bytecode.StaticReplaceBindingsAttribute;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.Config;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.Config.NotConfiguredException;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.ITranslationStates;
import org.eclipse.objectteams.otdt.internal.core.compiler.lifting.ArrayLifting;
import org.eclipse.objectteams.otdt.internal.core.compiler.lifting.Lifting;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.CallinCalloutBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.ITeamAnchor;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.RoleTypeBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.MethodModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.ModelElement;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.RoleModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.TeamModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.smap.SourcePosition;
import org.eclipse.objectteams.otdt.internal.core.compiler.smap.StepOverSourcePosition;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.AbstractStatementsGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.PredicateGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstClone;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstConverter;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstEdit;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.RoleTypeCreator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.TypeAnalyzer;

/**
 * After method mappings have been resolved on the signature level create the
 * AST for the wrapper methods.
 *
 * @author macwitte/haebor
 */
public class CallinImplementor extends MethodMappingImplementor
{
	public static final String OT_LOCAL = "_OT$local$"; //$NON-NLS-1$
	//_OT$role
	static final char[] ROLE_VAR_NAME = CharOperation.concat(IOTConstants.OT_DOLLAR_NAME, IOTConstants.ROLE);
	static final char[] OLD_IS_EXECUTING = "_OT$oldIsExecuting".toCharArray(); //$NON-NLS-1$
	private ClassScope _roleScope;

	/**
	 * Each instance is configured for one role.
	 * @param role
	 */
	public CallinImplementor(RoleModel role)
	{
		super(role);
		this._roleScope = role.getAst().scope; // we definitely have an AST here
		this.bindingDirection = TerminalTokens.TokenNameBINDIN;
	}
	/**
	 * Main entry from Dependencies.
	 * @return success?
	 */
	public boolean transform()
	{
		AbstractMethodMappingDeclaration[] methodMappings =	this._role.getAst().callinCallouts;
		if(methodMappings == null || methodMappings.length == 0)
		{
			// there are no mappings in this role!
			return true;
		}

		if (this._role._hasBindingAmbiguity) {
			for (int i = 0; i < methodMappings.length; i++)
				if (methodMappings[i].isCallin())
					this._roleScope.problemReporter().callinDespiteLiftingProblem(
										this._role.getBinding(),
										IProblem.CallinDespiteBindingAmbiguity,
										methodMappings[i]);
		}
		boolean result = true;
        CallinMappingDeclaration[] callinMappings = new CallinMappingDeclaration[methodMappings.length];
        int num = 0;
        LinkedList<CallinMappingDeclaration> staticReplaces = new LinkedList<CallinMappingDeclaration>();
		for (int idx = 0; idx < methodMappings.length; idx++)
		{
			AbstractMethodMappingDeclaration methodMapping = methodMappings[idx];
			if(!methodMapping.ignoreFurtherInvestigation && methodMapping.isCallin())
			{
				result &= createCallin((CallinMappingDeclaration) methodMapping);
                callinMappings[num++] = (CallinMappingDeclaration)methodMapping;
                if (methodMapping.isStaticReplace())
                	staticReplaces.add((CallinMappingDeclaration)methodMapping);
			}
		}
        System.arraycopy(
                callinMappings, 0,
                callinMappings = new CallinMappingDeclaration[num], 0,
                num);
        this._role.addAttribute(new CallinMethodMappingsAttribute(callinMappings));
        if (staticReplaces.size() > 0) {
        	CallinMappingDeclaration[] callins = new CallinMappingDeclaration[staticReplaces.size()];
        	staticReplaces.toArray(callins);
        	this._role.getTeamModel().addOrMergeAttribute(new StaticReplaceBindingsAttribute(callins));
        }
		return result;

	}

	/*
		class MyRole playedBy MyBase {
			MyRole myRoleMethod(int a, MyRole r, MyBase b) <- replace MyBase myBaseMethod(int a, MyRole r, MyBase b);
		}

		=============>>>>>>>

		public void _OT$MyRole$myRoleMethod(MyBase _OT$base_param,int _OT$param0)
		{
			MyRole _OT$role = _OT$liftToMyRole(_OT$base_param, LIFT_CALLIN_TARGET);
			_OT$role.myRoleMethod(_OT$param0);
		}
		Note(SH): for static methods:
			+ the _OT$base_param will receive a null value from the base-side chaining-wrapper.
	*/
	private boolean createCallin(CallinMappingDeclaration callinMappingDeclaration)
	{
		callinMappingDeclaration.updateTSuperMethods();

		// This binding is part of the interface part of a role:
		MethodBinding roleMethodBinding = callinMappingDeclaration.getRoleMethod();

		if(roleMethodBinding == null || !roleMethodBinding.isValidBinding()) // CLOVER: never true in jacks suite
		{
			// problemreporting already done in find-Base/Role-MethodBinding
			return false;
		}

        MethodSpec[] baseMethodSpecs = callinMappingDeclaration.baseMethodSpecs;
        for (int i = 0; i < baseMethodSpecs.length; i++) {
    		createWrapperMethod(callinMappingDeclaration, roleMethodBinding, baseMethodSpecs[i]);
        }
		return true;
	}

	/*
	    Precondition: MethodSpec have been resolved.
	    Basically, the code to be generated looks as follows. However,
	    several variants exist concerning results, predicates, ResultNotProvidedException.

		public void _OT$MyRole$myRoleMethod(MyBase _OT$base_arg, int _OT$param0)
		{
			oldIsExecutingCallin = _OT$setExecutingCallin();
			MyRole _OT$role = _OT$liftToMyRole(_OT$base_arg, LIFT_CALLIN_TARGET);
			try {
				_OT$role.myRoleMethod(_OT$param0);
			} finally {
				_OT$setExecutingCallin(oldIsExecutingCallin);
			}
		}

		NOTE: the callin wrapper actually uses the role interface throughout
		===== although using the class might seem easier, and might allow us
 			  not to create ifc-versions of callins. However, translation using
			  the class would require special treatment in several cases.
			  For this reason let's just stick to the standard way of using the ifc.
	 */
	private void createWrapperMethod(
			final CallinMappingDeclaration callinBindingDeclaration,
			final MethodBinding            roleMethodBinding,
			final MethodSpec               baseMethodSpec)
	{
		TypeDeclaration teamDecl = this._role.getTeamModel().getAst();

		final AstGenerator gen = new AstGenerator(
				callinBindingDeclaration.roleMethodSpec.sourceStart,
				callinBindingDeclaration.roleMethodSpec.sourceEnd);
		gen.replaceableEnclosingClass = teamDecl.binding;
		if (this._role.getClassPartBinding() != null)
			gen.replaceableBaseAnchor = this._role.getClassPartBinding().getField(IOTConstants._OT_BASE, true);

		// ---------- useful names: ------------
		//MyRole
		final char[] roleName = this._role.getName();
		//myRoleMethod
		char[] roleMethodName = roleMethodBinding.selector;
		//_OT$MyRole$myRoleMethod$myBaseMethod
		char[] newMethodName = makeWrapperName(callinBindingDeclaration, roleName, roleMethodName, baseMethodSpec.selector);

		//_OT$base_arg
		char[] otBaseArg = IOTConstants.BASE;

		TypeParameter[] typeParams= getTypeParameters(callinBindingDeclaration.hasSignature,
									   				  roleMethodBinding,
									   				  callinBindingDeclaration.roleMethodSpec,
									   				  gen);

		// ----------- method arguments ---------------
		Argument[] arguments = copyArguments(gen,
									callinBindingDeclaration.scope,
									baseMethodSpec.resolvedParameters(),
									baseMethodSpec);
		if (arguments != null && typeParams != null) {
			TypeBinding[] roleParams= callinBindingDeclaration.roleMethodSpec.resolvedParameters();
			Pair<Expression, Integer>[] mappingExpressions = callinBindingDeclaration.mappingExpressions;
			// for each arg in arguments: replace if corresponding role argument is type variable:
			for (int i= 0; i < arguments.length; i++) {
				TypeBinding mappedRoleParam= null;
				if (mappingExpressions != null) {
					// search mapped role parameter (j is role-signature index):
					for (int j= 0; j < mappingExpressions.length; j++) {
						if (mappingExpressions[j].second == i) {
							mappedRoleParam = roleParams[j];
							break;
						}
					}
				} else if (i < roleParams.length) {
					mappedRoleParam= roleParams[i];
				}
				if (mappedRoleParam != null && mappedRoleParam.isTypeVariable())
					arguments[i].type= gen.singleTypeReference(mappedRoleParam.internalName());
			}
		}

		// wrapper receives all base args (might filter them using param-mappings, or cut-off trailing unneeded)
		// prepend base argument into slot 0.
		if (arguments != null) {
			int len = arguments.length;
			System.arraycopy(arguments, 0, (arguments = new Argument[len+1]), 1, len);
		} else {
			arguments = new Argument[1];
		}

		final ReferenceBinding baseTypeBinding = this._role.getBaseTypeBinding();
		TypeReference baseTypeReference = gen.baseclassReference(baseTypeBinding);
		Argument baseArgument = gen.argument(otBaseArg, baseTypeReference);
		baseArgument.modifiers |= ClassFileConstants.AccFinal; // possibly the anchor for a role type
		baseArgument.isGenerated = true;
		arguments[0] = baseArgument;

		// ----------- return type ----------------
		// Only wrappers for replace callin mappings have a result:
		TypeBinding wrapperReturnType = callinBindingDeclaration.isReplaceCallin() ?
						wrapperReturnType = MethodModel.getReturnType(roleMethodBinding) :
						TypeBinding.VOID;
		if (baseMethodSpec.returnNeedsTranslation) {
			TypeBinding roleReturn = callinBindingDeclaration.realRoleReturn; // accounts for weakening
			int dims = wrapperReturnType.dimensions();

			// fail safe (without a witness):
			if (roleReturn == null)
				roleReturn = wrapperReturnType;

			// lowered return:
			wrapperReturnType = ((ReferenceBinding)roleReturn.leafComponentType()).baseclass();

			char[] liftCallSelector;
			// array?
			if (dims > 0) {
				wrapperReturnType = this._roleScope.createArrayType(wrapperReturnType, dims);
				liftCallSelector =
					new ArrayLifting().ensureTransformMethod(
							callinBindingDeclaration.scope,
							gen.thisReference(), wrapperReturnType, roleReturn, true)
						.selector;
			} else {
				liftCallSelector = Lifting.getLiftMethodName(roleReturn);
			}
			// now find the lift method for use in the CallinMethodMappings attribute
			TypeDeclaration teamType = this._roleScope.referenceContext;
			AbstractMethodDeclaration liftMethod = null;
			while (liftMethod == null) {
				if (teamType == null)
					throw new InternalCompilerError("Required lift method "+String.valueOf(liftCallSelector)+ 						  //$NON-NLS-1$
							                        " not found in scope of role "+String.valueOf(this._roleScope.referenceContext.name)); //$NON-NLS-1$
				if (teamType.isRole())
					teamType = teamType.getRoleModel().getInterfaceAst();
				liftMethod = TypeAnalyzer.findMethodDecl(teamType, liftCallSelector, 1);
				teamType = teamType.enclosingType;
			}
			callinBindingDeclaration.liftMethod =  liftMethod.binding;
		}
		// enhance arguments to pass runtime team-activation arguments:
		boolean isReturnBoxed = false; // has a basic type been converted to "Object"?
		TypeBinding baseReturnType = baseMethodSpec.resolvedMethod.returnType;
		if (callinBindingDeclaration.isReplaceCallin()) {
//			arguments = MethodSignatureEnhancer.enhanceArguments(
//								arguments,
//								new char[0],
//								true, // isWrapper
//								gen,
//								this._role.getWeavingScheme());
			if (wrapperReturnType.isBaseType()) {
				TypeBinding baseReturn = baseReturnType;
				isReturnBoxed = (baseReturn.isBaseType() && baseReturn != TypeBinding.VOID);
				wrapperReturnType = callinBindingDeclaration.scope.getJavaLangObject();
			}
			// (note that message send arguments are enhanced automatically,
			//  because for replace callins they are constructed from the wrapper signature)
		} else if (callinBindingDeclaration.callinModifier == TerminalTokens.TokenNameafter) {
			// pass result of base method.
			arguments = addResultArgument(arguments, callinBindingDeclaration, baseMethodSpec, gen);
		}
		for (Argument argument : arguments)
			argument.type.setBaseclassDecapsulation(DecapsulationState.REPORTED);

		// ----------- the method declaration ---------------
		int modifiers = ClassFileConstants.AccPublic;
		if (callinBindingDeclaration.isReplaceCallin())
			modifiers |= ExtraCompilerModifiers.AccCallin;
		MethodDeclaration newMethod = gen.method(teamDecl.compilationResult,
					modifiers,
					wrapperReturnType,
					newMethodName,
					arguments);
		newMethod.thrownExceptions = AstClone.copyExceptions(baseMethodSpec.resolvedMethod, gen);
		newMethod.isMappingWrapper = WrapperKind.CALLIN;
		newMethod.returnType.setBaseclassDecapsulation(DecapsulationState.REPORTED);
		newMethod.typeParameters= typeParams;
		
		gen.maybeAddTypeParametersToMethod(baseTypeBinding, newMethod);

		int iProblem = teamDecl.getTeamModel().canLiftingFail(this._role.getBinding());
		if (iProblem != 0)
			callinBindingDeclaration.addRoleLiftingProblem(this._role.getBinding(), iProblem);
		if (callinBindingDeclaration.rolesWithLiftingProblem != null)
			declareLiftingFailedException(callinBindingDeclaration, newMethod, gen);

		// -----   add and build and link the method -----
		AstEdit.addMethod(teamDecl, newMethod);
        callinBindingDeclaration.setWrapper(baseMethodSpec, newMethod);
        // TODO(SH): could optimize MethodInfo.maybeRegister()
        //           by marking callin to private role method (only they need copying).
        MethodModel.addCallinFlag(newMethod, IOTConstants.CALLIN_FLAG_WRAPPER);
        newMethod.model._declaringMappings = Collections.singletonList(callinBindingDeclaration);
        if (newMethod.hasErrors()) { // problems detected during creation of MethodBinding?
        	// CLOVER: never reached in jacks suite
        	AstEdit.removeMethod(teamDecl, newMethod.binding); // may be incomplete.
        	return;
        }
        setBaseArgBestName(newMethod, baseArgument);

		// ---------- deferred generation of statements: ----------

        // make values available to anonymous class:
        final TypeBinding finalWrapperReturnType = wrapperReturnType;
        final boolean     finalIsReturnBoxed     = isReturnBoxed;
        final RoleModel   finalRole              = this._role;

        MethodModel.getModel(newMethod).setStatementsGenerator(new AbstractStatementsGenerator() {
			@Override
			public boolean generateStatements(AbstractMethodDeclaration methodDecl) {
				return generateCallinStatements(
							(MethodDeclaration)methodDecl,
							callinBindingDeclaration,
							finalRole,
							roleMethodBinding,
							baseTypeBinding,
							baseMethodSpec,
							finalWrapperReturnType,
							finalIsReturnBoxed,
							gen);
			}
        });
	}
	/** Declare to throw LiftingFailedException and report problem for each role in callinBindingDeclaration#rolesWithBindingAmbiguity */
	protected void declareLiftingFailedException(CallinMappingDeclaration callinBindingDeclaration,
												 MethodDeclaration wrapperMethod,
												 AstGenerator gen) 
	{
		TypeReference liftingFailed = gen.qualifiedTypeReference(IOTConstants.O_O_LIFTING_FAILED_EXCEPTION);
		AstEdit.addException(wrapperMethod, liftingFailed, false/*resolve*/);
		// and report the problem(s)
		for (Map.Entry<ReferenceBinding, Integer> entry : callinBindingDeclaration.rolesWithLiftingProblem.entrySet())
			this._roleScope.problemReporter().callinDespiteLiftingProblem(entry.getKey(), entry.getValue(), callinBindingDeclaration);
	}
	/** Generate the statements for a callin wrapper method.
	 *
	 */
	boolean generateCallinStatements(
						MethodDeclaration callinWrapperDecl,
						CallinMappingDeclaration callinBindingDeclaration,
						RoleModel roleModel,
						MethodBinding roleMethodBinding,
						ReferenceBinding baseTypeBinding,
						MethodSpec baseMethodSpec,
						TypeBinding wrapperReturnType,
						boolean isReturnBoxed,
						AstGenerator gen)
	{
		if (callinBindingDeclaration.mappings == AbstractMethodMappingDeclaration.PENDING_MAPPINGS)
			return false; // cannot proceed, required info is not parsed.

		if (this._role.isRoleFile()) {
			this.synthGen = MethodModel.setupSourcePositionMapping(callinWrapperDecl,
																   this._role.getTeamModel().getAst(), 
													   			   this._role,
													   			   null);
			if (this.synthGen != null)
				gen = this.synthGen;
		}

		PredicateGenerator predGen = new PredicateGenerator(
											roleModel.getBinding(),
											callinBindingDeclaration.isReplaceCallin());

		char[] roleTypeName = roleModel.getInterfaceAst().name;

		//_OT$base_arg
		char[] otBaseArg = IOTConstants.BASE;

		//myRoleMethod
		char[] roleMethodName = roleMethodBinding.selector;

		TypeBinding[] roleParameters = roleMethodBinding.getSourceParameters();

        ArrayList<Statement> statements = new ArrayList<Statement>();

        // -------------- base predicate check -------
        char[] resultName = null;
    	if (   callinBindingDeclaration.callinModifier == TerminalTokens.TokenNameafter
        		&& baseMethodSpec.resolvedType() != TypeBinding.VOID)
        {
    		resultName = IOTConstants.RESULT;
        }
        Statement predicateCheck = predGen.createBasePredicateCheck(
        		callinBindingDeclaration, baseMethodSpec, resultName, gen);
        if (predicateCheck != null) {
        	statements.add(predicateCheck);
        }

		// ------------- support for reflective function isExecutingCallin():
		// boolean oldIsExecutingCallin = _OT$setExecutingCallin();
        // try { ... main dispatching statements ... }
        // finally { resetFlag }
		MessageSend resetFlag = setExecutingCallin(roleModel, statements);

		// from here on all statements go into the try block - with "finally { resetFlag(); }"
        ArrayList<Statement> tryStatements = new ArrayList<Statement>();

		
		// -------------- call receiver & arguments --------------
		//_OT$role.myRoleMethod(_OT$param0, ...);
		// or:
		//MyRole.myRoleMethod(_OT$param0, ...);
		// or:
		//MyTeam.this.myMethod(_OT$param0, ...);
		Expression receiver = null;
        Expression[] messageSendArguments = makeWrapperCallArguments(
				callinBindingDeclaration,
				callinWrapperDecl,
				baseMethodSpec,
				null,
				resultName != null /*hasResultArg*/);
        if (messageSendArguments == null) {
        	callinBindingDeclaration.tagAsHavingErrors();
        	return false;
        }
        // pack unmapped arguments (positions are set by above makeWrapperCallArguments):
        packUnmappedArgs(baseMethodSpec, callinBindingDeclaration, callinWrapperDecl, tryStatements, gen);

		// for role-side predicate
        Expression[] predicateArgs = null;
        int offset = callinBindingDeclaration.isReplaceCallin() ?
        										getMethodSignatureEnhancer().ENHANCING_ARG_LEN :
        										0;
        int plainLen = messageSendArguments.length-offset;
        boolean needRoleVar = false;
		if (roleMethodBinding.isStatic()) {
			receiver = gen.singleNameReference(roleMethodBinding.declaringClass.sourceName());
			// predicate args:
			if (offset > 0)
				System.arraycopy(messageSendArguments, offset, predicateArgs=new Expression[plainLen], 0, plainLen); // retrench
			else
				predicateArgs = messageSendArguments; 																 // nothing to retrench
			predicateArgs = maybeAddResultReference(callinBindingDeclaration, predicateArgs, resultName, gen);
		} else { // !roleMethodBinding.isStatic()
			if (!roleModel.getBinding().isCompatibleWith(roleMethodBinding.declaringClass)) {
				receiver = gen.qualifiedThisReference(TeamModel.strengthenEnclosing(roleModel.getBinding().enclosingType(), roleMethodBinding.declaringClass));
			} else {
				receiver = gen.singleNameReference(ROLE_VAR_NAME);
				needRoleVar = true;
	
				// receiver for private method (doesn't exist in ifc-part) needs to be casted to the class.
				// Scope.findMethod() takes care of visibility if isMethodMappingWrapper() is detected.
				if (roleMethodBinding.isPrivate())
					receiver = gen.castExpression(
										receiver,
							            gen.typeReference(roleModel.getClassPartBinding()),
										CastExpression.NEED_CLASS);
					// Note on using NEED_CLASS above:
					// even if role is ParameterizedTypeBinding Scope.findMethod() must find a RoleTypeBinding
					// in order to enter the branch that checks isMethodMappingWrapper()
					// with CastExpression.RAW a RawTypeBinding might occur that is not recognized by Scope.findMethod()
					// see testA12_genericRoleFeature16f() which reports bogus visibility problem is RAW is used.
			}
	
			//MyRole _OT$role = _OT$liftToMyRole(_OT$base_arg);
			if (needRoleVar)
				tryStatements.add(createLiftedRoleVar(callinBindingDeclaration, roleModel, baseTypeBinding, otBaseArg, gen));

			// store mapped arguments in local variables to use for predicate check
			// and wrapper call.
			// first create local variable for real arguments:
			assert roleParameters.length  == plainLen;
			Expression[] newArgs = new Expression[plainLen];
			for (int i = offset; i < messageSendArguments.length; i++) {
 				char[] localName = (OT_LOCAL+i).toCharArray();
 				tryStatements.add(gen.localVariable(
 						localName,
						roleParameters[i-offset],
 						new PotentialRoleReceiverExpression(messageSendArguments[i], ROLE_VAR_NAME, gen.typeReference(roleModel.getClassPartBinding()))));
				newArgs[i-offset] = gen.singleNameReference(localName);
			}
			// predicate arguments (w/o enhancement but w/ result_opt):
			predicateArgs = maybeAddResultReference(callinBindingDeclaration, newArgs, resultName, gen);
            // prepend (generated) enhanced arguments
			System.arraycopy(
					newArgs, 0,
					newArgs = new Expression[messageSendArguments.length], offset,
					plainLen);
			for(int i = 0; i<offset; i++) {
				newArgs[i] = messageSendArguments[i]; // generated arg is not mapped, nor stored
			}
			messageSendArguments = newArgs; // from now on use local names instead of mapped expressions
		} // closes if(roleMethodBinding.isStatic())

		// role side predicate:
        predicateCheck = predGen.createPredicateCheck(
        		callinBindingDeclaration,
        		roleModel.getAst(),
				receiver,
				predicateArgs,
				messageSendArguments,
				gen);
		if (predicateCheck != null)
        	// predicateCheck(_OT$role)
			tryStatements.add(predicateCheck);

		// ------------- the role message send:
		MessageSend roleMessageSend = gen.messageSend(receiver, roleMethodName, messageSendArguments);
		roleMessageSend.isPushedOutRoleMethodCall = true;

		// debugging should skip the return statement.
		AstGenerator stepOverGen = new AstGenerator(STEP_OVER_SOURCEPOSITION_START, STEP_OVER_SOURCEPOSITION_END);
		// ---------------- store or ignore the result:
		if (   callinBindingDeclaration.isReplaceCallin())
		{
			//   <WrapperReturn> _OT$result;
			//   try {
			//     _OT$result = _OT$role.myRoleMethod(_OT$param0);
			//   finally {
			//     _OT$setExecutingCallin(oldIsExecutingCallin);
			//   }

			//   $if isReturnBoxed
			//     if (_OT$result == null) throw new ResultNotProvidedException(..);
			//   $endif

			//   $if isResultMapped
			//     return <expressionMappedToResult>;
			//   $else
			//     return _OT$result;
			//   $endif

			Expression roleMessageSendExpression = roleMessageSend;
			
			if (roleMethodBinding.returnType.isArrayType()) {
				// if return from role method requires array-lowering, we must determine how to access the team (receiver of lowering-method):
				TypeBinding returnLeaf = roleMethodBinding.returnType.leafComponentType();
				findEnclosingTeam: 
				if (returnLeaf.isRole()) {
					ReferenceBinding returnEnclosing = returnLeaf.enclosingType(); // the team type containing the returned role
					ReferenceBinding currentType = roleModel.getBinding();
					while ((currentType = currentType.enclosingType()) != null)	   // traverse all types accessible as this$<n>
						if (TypeBinding.equalsEquals(currentType, returnEnclosing))
							break findEnclosingTeam; // successful
					// not found, which means this$<n> is not a suitable receiver for array lowering, must use 'receiver' instead:
					roleMessageSendExpression = new PotentialLowerExpression(roleMessageSend, wrapperReturnType, receiver);
				}
			}
			callinBindingDeclaration.resultVar = gen.localVariable(
											IOTConstants.OT_RESULT, wrapperReturnType, null);
			callinBindingDeclaration.resultVar.type.setBaseclassDecapsulation(DecapsulationState.REPORTED);
			tryStatements.add(callinBindingDeclaration.resultVar);
			tryStatements.add(gen.assignment(
									gen.singleNameReference(IOTConstants.OT_RESULT),
									roleMessageSendExpression));
			
			// ResultNotProvidedException?
			if (isReturnBoxed && !callinBindingDeclaration.isResultMapped)
			{
				tryStatements.add(genResultNotProvidedCheck(
						this._role.getTeamModel().getBinding().sourceName(),
						roleTypeName, roleMethodBinding, baseTypeBinding, baseMethodSpec, gen));
			}
			// ------------- possibly convert using result mapping
			if (   callinBindingDeclaration.mappings != null
					&& callinBindingDeclaration.isResultMapped)
			{
				tryStatements.add(
						stepOverGen.returnStatement(
								new PotentialRoleReceiverExpression(
										callinBindingDeclaration.getResultExpression(baseMethodSpec, isReturnBoxed, stepOverGen),
										ROLE_VAR_NAME,
										gen.typeReference(roleModel.getClassPartBinding()))));
			} else {
				tryStatements.add(
						stepOverGen.returnStatement(stepOverGen.singleNameReference(IOTConstants.OT_RESULT)));
			}
			
			TryStatement tryFinally = gen.tryFinally(
								tryStatements.toArray(new Statement[tryStatements.size()]),
								new Statement[] {resetFlag});
// for debugging:
//			tryFinally.catchArguments = new Argument[] {
//				gen.argument("e1".toCharArray(), gen.singleTypeReference("RuntimeException".toCharArray())),
//				gen.argument("e2".toCharArray(), gen.singleTypeReference("Error".toCharArray()))
//			};
//			tryFinally.catchBlocks = new Block[] {
//				gen.block(new Statement[] {
//						gen.messageSend(
//								gen.singleNameReference("e1".toCharArray()),
//								"printStackTrace".toCharArray(),
//								null),
//						gen.throwStatement(gen.singleNameReference("e1".toCharArray()))
//				}),
//				gen.block(new Statement[] {
//						gen.messageSend(
//								gen.singleNameReference("e2".toCharArray()),
//								"printStackTrace".toCharArray(),
//								null),
//						gen.throwStatement(gen.singleNameReference("e2".toCharArray()))
//				})
//			};
			statements.add(tryFinally);

		} else {
			// try {
			//    _OT$role.myRoleMethod(_OT$param0);
			// finally {
			//     _OT$setExecutingCallin(oldIsExecutingCallin);
			// }
			tryStatements.add(roleMessageSend);
			statements.add(gen.tryFinally(
					tryStatements.toArray(new Statement[tryStatements.size()]),
					new Statement[] {resetFlag}));
			statements.add(stepOverGen.returnStatement(null)); // empty return to ensure step-over in the end
		}

		callinWrapperDecl.setStatements(statements.toArray(new Statement[statements.size()]));

		// parameter mappings are detected during makeWrapperCallArguments
		// ----------- byte code attribute -------------
        if (callinBindingDeclaration.positions != null) {
            MethodModel model = MethodModel.getModel(callinWrapperDecl);
        	model.addAttribute(new CallinParamMappingsAttribute(callinBindingDeclaration));
        }

		return true;
	}
	/**
	 * Create a call to _OT$setIsExecutingCallin()
	 * @param roleModel		the role holding the current callin mapping
	 * @param statements 	where to add the call
	 * @return an expression for resetting the flag.
	 */
	public static MessageSend setExecutingCallin(RoleModel roleModel, List<Statement> statements) {
		// use a separate gen for stepOver, so we don't have to switch back/forth its positions:
		AstGenerator stepOverGen = new AstGenerator(STEP_OVER_SOURCEPOSITION_START, STEP_OVER_SOURCEPOSITION_END);
		// mark as step_over:
		roleModel.getLineNumberProvider().addLineInfo(roleModel.getBinding(), STEP_OVER_LINENUMBER, -1);
		// ignore line number returned by addLineInfo but use the corresponding source position

		statements.add(stepOverGen.localVariable(
				OLD_IS_EXECUTING,
				TypeBinding.BOOLEAN,
				stepOverGen.messageSend(
						stepOverGen.thisReference(),
						IOTConstants.SET_EXECUTING_CALLIN,
						new Expression[]{ stepOverGen.booleanLiteral(true) })));

		// _OT$setExecutingCallin(oldIsExecutingCallin); (to be inserted below)
		return stepOverGen.messageSend(
				stepOverGen.thisReference(),
				IOTConstants.SET_EXECUTING_CALLIN,
				new Expression[] { stepOverGen.singleNameReference(OLD_IS_EXECUTING)} );
	}
	private Statement createLiftedRoleVar(CallinMappingDeclaration 	callinBindingDeclaration, 
										  RoleModel 				roleModel,
										  ReferenceBinding 			baseTypeBinding, 
										  char[] 					otBaseArg, 
										  AstGenerator 				gen) 
	{
		MessageSend liftCall = Lifting.liftCall(
				callinBindingDeclaration.scope,
				ThisReference.implicitThis(),
				gen.baseNameReference(otBaseArg),
				baseTypeBinding,
				roleModel.getBinding(),
				callinBindingDeclaration.isReplaceCallin()/* needLowering*/);

		ReferenceBinding roleVarType = roleModel.getInterfacePartBinding();
		// does lifting use type parameters? If so, use the same types as type arguments for the role variable
		MethodBinding[] liftMethod = roleVarType.enclosingType().getMethods(liftCall.selector);
		if (liftMethod != null && liftMethod.length > 0) {
			TypeBinding[] typeArguments = liftMethod[0].typeVariables();
			if (typeArguments != Binding.NO_TYPE_VARIABLES)
				try {
					// FIXME: pass null annotations, once JDT supports those on a type declaration.
					roleVarType = Config.getLookupEnvironment().createParameterizedType(roleVarType, typeArguments, null, -1, roleModel.getBinding().enclosingType(), Binding.NO_ANNOTATIONS);
				} catch (NotConfiguredException e) {
					e.logWarning("Cannot lookup parameterized type"); //$NON-NLS-1$
				}
		}
		return gen.localVariable(ROLE_VAR_NAME, roleVarType, liftCall);
	}
	/**
	 * Assemble message send arguments plus perhaps a result reference to
	 * yield argument expressions for a predicate call.
	 */
	private Expression[] maybeAddResultReference(CallinMappingDeclaration callinBindingDeclaration,
										         Expression[] messageSendArguments,
										         char[] resultName,
										         AstGenerator gen)
	{
		Expression[] predicateArgs = null;
		if (callinBindingDeclaration.hasSignature) {
			predicateArgs = messageSendArguments;
			if (resultName != null) // has resultVar (after with non-void base return)
			{
				int l = messageSendArguments.length;
				System.arraycopy(messageSendArguments, 0, predicateArgs = new Expression[l+1], 0, l);
				predicateArgs[l] = gen.baseNameReference(resultName);
			}
		}
		return predicateArgs;
	}
	/*
	 * create and add statements which pack unused args into _OT$unusedArgs.
	 */
	private void packUnmappedArgs(MethodSpec               baseMethodSpec,
								  CallinMappingDeclaration callinBindingDeclaration,
								  MethodDeclaration 	   callinWrapper,
								  ArrayList<Statement>	   statements,
								  AstGenerator 			   gen)
	{
        int[] unmapped = callinBindingDeclaration.getUnmappedBasePositions(baseMethodSpec);
        if (unmapped.length == 0)
        	return;
        SourcePosition savePos = gen.getSourcePosition();
        gen.setSourcePosition(new StepOverSourcePosition());
        try {
	        // must have signatures if mapping present.
	        int unusedIdx;
	        int basePos = 1; // skip base object
	        if (callinBindingDeclaration.isReplaceCallin()) {
	        	// we have enhanced arguments including _OT$unusedArgs
	        	basePos += getMethodSignatureEnhancer().ENHANCING_ARG_LEN;
				unusedIdx = 0;
				// check what is already in _OT$unusedArgs:
				if (baseMethodSpec.isCallin()) {
					unusedIdx = getMethodSignatureEnhancer().ENHANCING_ARG_LEN;
					if (baseMethodSpec.isStatic()) // FIXME(SH): Static role methods other the callin?
						unusedIdx += 2; // a (dummyInt,MyTeam) pair of synthetics
				}
	        } else {
	        	// no enhanced arguments, means also: no _OT$unusedArgs available yet.
	        	statements.add(gen.localVariable(
	        			getMethodSignatureEnhancer().UNUSED_ARGS,
	        			new ArrayQualifiedTypeReference(
	        					TypeConstants.JAVA_LANG_OBJECT,
	        					1, // dims
	        					new long[]{gen.pos, gen.pos, gen.pos}),
	        			gen.arrayAllocation(
	        					gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT),
	        					unmapped.length, // constant dims
	        					null)));
	        	unusedIdx = 0;
	        }
	        for (int i = 0; i < unmapped.length; i++) {
	    		Argument arg = callinWrapper.arguments[basePos+unmapped[i]];
	    		TypeBinding paramType = arg.type.resolvedType;
				Expression rhs = gen.singleNameReference(arg.name);
				if (paramType.isBaseType())
					rhs = gen.createBoxing(rhs, (BaseTypeBinding)paramType);
				statements.add(
					gen.assignment(
						new ArrayReference(
								gen.singleNameReference(getMethodSignatureEnhancer().UNUSED_ARGS),
								gen.intLiteral(unusedIdx++)),
						rhs));
			}
        } finally {
        	gen.setSourcePosition(savePos);
        }
	}

	/**
	 * Add a method argument for passing the result of a base method to an after callin.
	 *
	 * Note: since this argument (if added) is called "result" for the sake of usage
	 * in parameter mappings, care must be taken, that this does not collide with
	 * passing the result of the role method back to the base (via another variable
	 * called "result").
	 *
	 * @param arguments      arguments we have so far
	 * @param mapping        declaration on behalf of which wrappers are generated
	 * @param baseMethodSpec base method being bound
	 * @param gen            Generator
	 * @return the new argument array including the generated result argument
	 */
	private Argument[] addResultArgument(
			Argument[]               arguments,
			CallinMappingDeclaration mapping,
			MethodSpec               baseMethodSpec,
			AstGenerator             gen)
	{
		Argument[] newArgs = new Argument[arguments.length+1];
		newArgs[0] = arguments[0];
		TypeBinding baseReturnType = baseMethodSpec.resolvedMethod.returnType;
		if (baseReturnType == TypeBinding.VOID)
			return arguments;

		ITeamAnchor baseSideAnchor = RoleTypeCreator.getPlayedByAnchor(mapping.scope);

		TypeReference baseTypeRef = getAnchoredTypeReference(gen, baseSideAnchor, baseReturnType);
		if (baseTypeRef == null) // CLOVER: never false in jacks suite
			baseTypeRef = gen.typeReference(baseReturnType);

		newArgs[1] = gen.argument(IOTConstants.RESULT, baseTypeRef);
		newArgs[1].isGenerated = true;
		if (arguments.length > 1)
			System.arraycopy(arguments, 1, newArgs, 2, arguments.length-1);

		mapping.resultVar = newArgs[1];
		return newArgs;
	}
	/**
	 * Assemble the name for a callin wrapper and ensure it is unique within the current team hierarchy.
	 *
	 * @param callinBindingDeclaration
	 * @param roleName
	 * @param roleMethodName
	 * @param baseMethodName
	 * @return the name
	 */
	private char[] makeWrapperName(
			CallinMappingDeclaration callinBindingDeclaration,
			char[]                   roleName,
			char[]                   roleMethodName,
			char[]                   baseMethodName)
	{
		char[] newMethodName =
				CharOperation.concatWith(
						new char[][] {
								CharOperation.concat(IOTConstants.OT_DOLLAR_NAME, roleName),
								roleMethodName,
								baseMethodName},
						'$');
		MethodBinding existingMethod;
		char[] currentName = newMethodName;
		int i=0;
		do {
			existingMethod = this._role.getTeamModel().getBinding().getMethod(
					callinBindingDeclaration.scope, currentName);
			if (existingMethod != null)
				currentName = CharOperation.concat(
						newMethodName,
						String.valueOf(i++).toCharArray(), '$');
		} while (existingMethod != null);
		return currentName;
	}


	/**
	 * Record that the base argument is equivalent to the base field.
	 * @param newMethod
	 */
	private void setBaseArgBestName(MethodDeclaration newMethod, Argument baseArg)
	{
		// base arg is always first, so binding cannot disturb argument order.
		baseArg.bind(newMethod.scope, baseArg.type.resolvedType, false);
		ITeamAnchor baseArgBinding = newMethod.arguments[0].binding;
		// lookup _OT$base:
		ReferenceBinding roleBinding = this._role.getBinding();
		if (   !roleBinding.isHierarchyInconsistent()
			&& !this._role.hasBaseclassProblem()) // CLOVER: never false in jacks suite
		{
			// have no base field if hierarchy is inconsistent (see TPX-214).
			ITeamAnchor baseField = TypeAnalyzer.findField(
										roleBinding, IOTConstants._OT_BASE, /*static*/false, /*outer*/true,
										ITranslationStates.STATE_ROLE_HIERARCHY_ANALYZED);
			// link both vars:
			if (baseField != null) {
				baseArgBinding.shareBestName(baseField);
			} else if (roleBinding.isRegularInterface()) {
				// OK!?
			} else {
				// Notes: Observed this while preparing the NODe-tutorial.
				//        Reoccurred as TPX-491, fixed by v14499.
				throw new InternalCompilerError("Role has no base field: "+new String(roleBinding.readableName())); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Overrides inherited method to account for signature enhancing.
	 * Do this by extracting the relevant slice of parameters from the
	 * wrapper's parameters.
	 */
	@Override
	TypeBinding[] getImplementationParamters(
			AbstractMethodMappingDeclaration methodMapping, MethodDeclaration wrapperMethod)
	{
		if (methodMapping.isReplaceCallin())
		{
			// before/after callins are not enhanced

			MethodBinding roleMethod = methodMapping
							.getImplementationMethodSpec()
							.resolvedMethod;
			TypeBinding[] roleParameters = roleMethod.parameters;
			return roleParameters;
		}
		return super.getImplementationParamters(methodMapping, wrapperMethod);
	}

	/**
	 * Implements inherited abstract method.
	 *
	 * Note that as a side effect, this method modifies methodMapping.mappings!
	 *
	 * @param methodMapping      lookup method spec and parameter mapping here
	 * @param wrapperDeclaration use args of this method if no mapping is involved
	 * @param implParameters     parameters of the implemented method to invoke (possibly enhanced)
	 * @param idx                argument position on the target side
	 * @param hasResultArgument  as a 'result' argument been prepended to the wrapper args?
	 * @param sourceMethodSpec   this signature defines the provided args
	 * @return the expression to pass to the implemented method.
	 */
	@Override
	Expression getArgument(
			AbstractMethodMappingDeclaration methodMapping,
			MethodDeclaration       		 wrapperDeclaration,
			TypeBinding[]                    implParameters,
			int		      					 idx,
			boolean                          hasResultArgument,
			final MethodSpec				 sourceMethodSpec)
	{
		final MethodSpec implementationMethodSpec = methodMapping.getImplementationMethodSpec();

	    Expression mappedArgExpr = null;
	    int        pos           = -1;
	    char[]     targetArgName = null;

		int generatedArgsLen = methodMapping.isReplaceCallin() ?
									getMethodSignatureEnhancer().ENHANCING_ARG_LEN:
									0;
		final int srcIdx = idx-generatedArgsLen; // index into source-code signatures.
		int wrapperPrefixLen = hasResultArgument ? 2 : 1; // skip prepended base_arg, and possibly result arg

		if (   methodMapping.mappings == null           // have no parameter mapping
			|| methodMapping.mappings.length == 0
		    || idx < generatedArgsLen)                  // don't map generated args.
	    {
			targetArgName	  = idx+wrapperPrefixLen < wrapperDeclaration.arguments.length
								? wrapperDeclaration.arguments[idx+wrapperPrefixLen].name
								: CharOperation.concat(IOTConstants.OT_DOLLAR_NAME, "missingArg".toCharArray()); //$NON-NLS-1$
	        MethodSpec rmSpec = methodMapping.roleMethodSpec;
			mappedArgExpr     = genSimpleArgExpr(targetArgName, rmSpec);
			if (idx >= generatedArgsLen)
				mappedArgExpr.resolve(wrapperDeclaration.scope); // resolved type needed in liftCall().
	    }
	    else
	    {
	    	targetArgName = implementationMethodSpec.arguments[srcIdx].name;
	    	// retrieve info collected during analyzeParameterMappings:
	    	Pair<Expression,Integer> mapper = methodMapping.mappingExpressions[srcIdx];
			mappedArgExpr = mapper.first;
			if (mapper.second != null)
				pos = mapper.second.intValue();
	    }
	    if (mappedArgExpr != null) {
	    	SourceTypeBinding roleType = methodMapping.scope.enclosingSourceType();

	    	if (idx >= implParameters.length) // CLOVER: never true in jacks suite
	    		return mappedArgExpr; // arg is invisible to receiver, don't lift
			TypeBinding expectedType = implParameters[idx];

			// arg might have been weakened:
			if (   expectedType.isRole()
				&& TypeBinding.notEquals(expectedType.enclosingType(), roleType.enclosingType()))
						expectedType = TeamModel.strengthenRoleType(roleType, expectedType);

			AstGenerator gen = new AstGenerator(mappedArgExpr.sourceStart, mappedArgExpr.sourceEnd);
			Expression receiver = null;
			TypeBinding expectedLeaf = expectedType.leafComponentType();
			if (   RoleTypeBinding.isRoleWithoutExplicitAnchor(expectedLeaf)
				&& TypeBinding.equalsEquals(roleType.getRealClass(), ((ReferenceBinding)expectedLeaf).enclosingType()))
			{
				// expectedType is a role of the current role(=team),
				// use the role as the receiver for the lift call:
				receiver = gen.singleNameReference(ROLE_VAR_NAME);
			}
			if (sourceMethodSpec.hasSignature) {
				if (sourceMethodSpec.argNeedsTranslation(srcIdx)) {
					mappedArgExpr.tagReportedBaseclassDecapsulation();
					return Lifting.liftCall(methodMapping.scope,
											receiver != null ? receiver : gen.qualifiedThisReference(expectedLeaf.enclosingType()),
											mappedArgExpr,
											sourceMethodSpec.resolvedParameters()[srcIdx],
											expectedType,
											methodMapping.isReplaceCallin()/*needLowering*/);
				}
				if (methodMapping.mappings == null)
					// we have signatures and no parameter mapping.
					// if no need for translation has been recorded, it IS not needed.
					return mappedArgExpr;
			}
			// don't know yet whether lifting is actually needed (=>potentially)
			Expression liftExpr =
				gen.potentialLift(receiver, mappedArgExpr, expectedType, methodMapping.isReplaceCallin());  // reversible?

			// if param mappings are present, connect the PLE to the method spec for propagating translation flag
			// (CallinMethodMappingsAttribute needs to know whether lifting is actually needed.)
			if (methodMapping.mappings != null && liftExpr instanceof PotentialLiftExpression) {
				final int srcPos = pos;
				((PotentialLiftExpression)liftExpr).onLiftingRequired(new Runnable() {@Override
				public void run() {
					if (srcPos != -1)
						sourceMethodSpec.argNeedsTranslation[srcPos] = true;
					implementationMethodSpec.argNeedsTranslation[srcIdx] = true; 
				}});
			}

			return liftExpr;
	    }
	    wrapperDeclaration.scope.problemReporter().unmappedParameter(
	            targetArgName,
	            implementationMethodSpec,
				methodMapping.isCallout());
	    return null;
	}

	/**
	 *	gen:  if (result == null) throw new ResultNotProvidedException(..);
	 *
	 * @param roleName
	 * @param roleMethodBinding
	 * @param baseTypeBinding
	 * @param baseMethodSpec
	 * @param gen
	 * @return the assembled iff statement
	 */
	public static Statement genResultNotProvidedCheck(char[] teamName, char[] roleName, MethodBinding roleMethodBinding, TypeBinding baseTypeBinding, MethodSpec baseMethodSpec, AstGenerator gen) {

		String errMsg = Messages.bind(
				"(team: {0}, role: {1}, method {2})\n"+ //$NON-NLS-1$
				"Base call to {3}.{4} is missing",  //$NON-NLS-1$
				new Object[] {
				   new String(teamName),
				   new String(roleName),
				   new String(roleMethodBinding.readableName()),
				   new String(baseTypeBinding.readableName()),
				   new String(baseMethodSpec.resolvedMethod.readableName())
				}
		);

		return gen.ifStatement(
				new EqualExpression(
					gen.singleNameReference(IOTConstants.OT_RESULT),
					gen.nullLiteral(),
					OperatorIds.EQUAL_EQUAL),
				gen.block(new Statement[] {
					gen.throwStatement(
						gen.allocation(
							gen.qualifiedTypeReference(IOTConstants.ORG_OBJECTTEAMS_RESULT_NOT_PROVIDED),
							new Expression[] {
								gen.stringLiteral(errMsg.toCharArray())
							}))
				}));
	}

	/**
	 * the expression _OT$role.roleMethod(..) should not wrap its
	 * return type anchored to the generated team anchor _OT$role,
	 * because the method should actually be seen as being within
	 * the scope of this role already, although, physically it is part of
	 * the team.
	 *
	 * @param scope use the scope to determine if we are acutally within
	 *        a callin wrapper.
	 * @param receiver if this is _OT$role this is the role method call.
	 * @return true if the return type should not be wrapped further.
	 */
	public static boolean avoidWrapRoleType(BlockScope scope, Expression receiver) {
		MethodScope methodScope = scope.methodScope();
		if (methodScope != null) { // CLOVER: never false in jacks suite
			AbstractMethodDeclaration refMethod = methodScope.referenceMethod();
			if (   refMethod != null
				&& refMethod.isMappingWrapper._callin())
			{
				if (   receiver instanceof SingleNameReference
					&& CharOperation.equals(
							((SingleNameReference)receiver).token,
							CharOperation.concat(IOTConstants.OT_DOLLAR_NAME, IOTConstants.ROLE)))
					return true;
			}
		}
		return false;
	}
	/** Callin to private must be copied if role is copy-inherited,
	 *  because the static method to access the role method exists only
	 *  in the exact role.
	 */
	public static void checkCopyCallinBinding(CallinMethodMappingsAttribute attr, ModelElement element) {
		if (!(element instanceof RoleModel))
			return;
		RoleModel roleModel = (RoleModel)element;
		TypeDeclaration teamDecl = roleModel.getTeamModel().getAst();
		if (teamDecl == null)
			return;
		CallinMethodMappingsAttribute.Mapping[] mappings = attr._mappings;
		for (CallinMethodMappingsAttribute.Mapping mapping : mappings)
			if (mapping.roleMethodIsPrivate())
				copyCallinTo(mapping, teamDecl);

	}
	private static void copyCallinTo(CallinMethodMappingsAttribute.Mapping mapping, TypeDeclaration teamDecl)
	{
		char[][] wrapperNames = mapping.getWrapperNames();
		char[][] wrapperSignatures = mapping.getWrapperSignatures();
		ReferenceBinding teamBinding = mapping._binding._declaringRoleClass.enclosingType();
		if (teamBinding == null || !teamBinding.superclass().isTeam())
			return;

		// lookup wrapper method in the super-team:
		teamBinding = teamBinding.superclass();
		for (int i=0; i<wrapperNames.length; i++) {
			MethodBinding[] methods = teamBinding.getMethods(wrapperNames[i]);
			methods: for (int j = 0; j < methods.length; j++) {
				if (CharOperation.equals(wrapperSignatures[i], methods[i].signature()))
				{
					copyOneCallinTo(methods[i], mapping._binding, teamDecl);
					break methods;
				}
			}
		}
	}
	private static void copyOneCallinTo(MethodBinding method, CallinCalloutBinding callinBinding, TypeDeclaration teamDecl)
	{
		// cf. CopyInheritance.copyMethod()
		AstGenerator gen = new AstGenerator(teamDecl.sourceStart, teamDecl.sourceEnd);
		AbstractMethodDeclaration newMethod = AstConverter.createMethod(
											method,
											teamDecl.binding,
											teamDecl.compilationResult,
											DecapsulationState.REPORTED,
											gen);

		MethodBinding origin = (method.copyInheritanceSrc != null) ?
									method.copyInheritanceSrc :
									method;
		AstEdit.addMethod(teamDecl, newMethod, false /*not synthetic*/, false/*addToFront*/, origin);

	    newMethod.binding.copiedInContext = teamDecl.binding.enclosingType();

	    MethodModel newModel = MethodModel.getModel(newMethod);
	    newModel.addAttribute(CopyInheritanceSourceAttribute.copyInherSrcAttribute(origin, newModel));
    	// copy down some more properties:
   		MethodModel.saveReturnType(newMethod.binding, MethodModel.getReturnType(method));

	    newMethod.isMappingWrapper = WrapperKind.CALLIN;
	}
}
