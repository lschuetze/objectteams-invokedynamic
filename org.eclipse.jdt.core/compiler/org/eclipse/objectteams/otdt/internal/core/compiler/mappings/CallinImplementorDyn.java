/** 
 * This file is part of "Object Teams Development Tooling"-Software
 * 
 * Copyright 2009, 2015 Stephan Herrmann
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors: Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.mappings;

import static org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccPublic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.CastExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.OperatorIds;
import org.eclipse.jdt.internal.compiler.ast.Reference;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions.WeavingScheme;
import org.eclipse.jdt.internal.compiler.lookup.BaseTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.parser.TerminalTokens;
import org.eclipse.objectteams.otdt.core.compiler.IOTConstants;
import org.eclipse.objectteams.otdt.core.compiler.Pair;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.AbstractMethodMappingDeclaration;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.CallinMappingDeclaration;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.MethodSpec;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PotentialLiftExpression;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PotentialLowerExpression;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PotentialRoleReceiverExpression;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.PrivateRoleMethodCall;
import org.eclipse.objectteams.otdt.internal.core.compiler.bytecode.OTDynCallinBindingsAttribute;
import org.eclipse.objectteams.otdt.internal.core.compiler.lifting.Lifting;
import org.eclipse.objectteams.otdt.internal.core.compiler.lifting.Lowering;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.CallinCalloutBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.DependentTypeBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.RoleTypeBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.MethodModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.RoleModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.TeamModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.AbstractStatementsGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.MethodSignatureEnhancer;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.PredicateGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.ReplaceResultReferenceVisitor;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.TeamMethodGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstEdit;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.RoleTypeCreator;

/**
 * This class translates callin binding to the dynamic weaving strategy.
 * This strategy is enabled by defining the property <code>ot.weaving=dynamic</code> 
 * 
 * @author stephan
 * @since 1.3.0M3
 */
public class CallinImplementorDyn extends MethodMappingImplementor {

	//_OT$role
	static final char[] ROLE_VAR_NAME = CharOperation.concat(IOTConstants.OT_DOLLAR_NAME, IOTConstants.ROLE);

	// method names
	public static final char[] OT_CALL_BEFORE  = "_OT$callBefore".toCharArray(); //$NON-NLS-1$
	public static final char[] OT_CALL_AFTER   = "_OT$callAfter".toCharArray(); //$NON-NLS-1$
	public static final char[] OT_CALL_REPLACE = "_OT$callReplace".toCharArray(); //$NON-NLS-1$
	// used for base calls:
	public static final char[] OT_CALL_NEXT        = "callNext".toCharArray(); //$NON-NLS-1$
	//  - both the team version (II[Object;) and the base version (I[Object;)
	public static final char[] OT_CALL_ORIG_STATIC = "_OT$callOrigStatic".toCharArray(); //$NON-NLS-1$

	private static final char[] OT_TERMINAL_CALL_NEXT = "_OT$terminalCallNext".toCharArray(); //$NON-NLS-1$
	
	// variable names (arguments ...)
	static final char[] TEAMS 			= "teams".toCharArray(); //$NON-NLS-1$
	static final char[] INDEX			= "index".toCharArray(); //$NON-NLS-1$
	static final char[] CALLIN_ID 		= "callinID".toCharArray(); //$NON-NLS-1$
	static final char[] BOUND_METHOD_ID = "boundMethodID".toCharArray(); //$NON-NLS-1$
	static final char[] ARGUMENTS 		= "arguments".toCharArray(); //$NON-NLS-1$
	static final char[] _OT_RESULT		= "_OT$result".toCharArray(); //$NON-NLS-1$
	static final char[] RESULT		 	= "result".toCharArray(); //$NON-NLS-1$
	static final String LOCAL_ROLE 		= "local$role$"; //$NON-NLS-1$
	static final char[] _BASE$          = "_base$".toCharArray(); //$NON-NLS-1$
	
	// for call next:
	private static final char[] BASE_CALL_ARGS  = "baseCallArguments".toCharArray();   //$NON-NLS-1$
	private static final char[] BASE_CALL_FLAGS = "baseCallFlags".toCharArray(); //$NON-NLS-1$

	// for call{replace,before,after}:
	static final char[][] REPLACE_ARG_NAMES = new char[][]{_BASE$, TEAMS, INDEX, CALLIN_ID, BOUND_METHOD_ID, ARGUMENTS};
	static final char[][] BEFORE_ARG_NAMES = new char[][]{_BASE$, CALLIN_ID, BOUND_METHOD_ID, ARGUMENTS};
	static final char[][] AFTER_ARG_NAMES = new char[][]{_BASE$, CALLIN_ID, BOUND_METHOD_ID, ARGUMENTS, _OT_RESULT};

	protected static final String OT_LOCAL = "_OT$local$"; //$NON-NLS-1$

	static final char[] CATCH_ARG = "_OT$caughtException".toCharArray(); //$NON-NLS-1$

	
	private ClassScope _roleScope;

	/**
	 * Main entry from Dependencies - roles.
	 * 
	 * Generates the byte code attributes.
	 */
	public void transformRole(RoleModel role)
	{
		this._role = role;
		this._roleScope = role.getAst().scope; // we definitely have an AST here
		this.bindingDirection = TerminalTokens.TokenNameBINDIN;
		
		AbstractMethodMappingDeclaration[] methodMappings =	this._role.getAst().callinCallouts;
		if(methodMappings == null || methodMappings.length == 0)
		{
			// there are no mappings in this role!
			return;
		}

		if (this._role._hasBindingAmbiguity) {
			for (int i = 0; i < methodMappings.length; i++) {
				this._roleScope.problemReporter().callinDespiteLiftingProblem(
									this._role.getBinding(),
									IProblem.CallinDespiteBindingAmbiguity,
									methodMappings[i]);
			}
		}
        CallinMappingDeclaration[] callinMappings = new CallinMappingDeclaration[methodMappings.length];
        int num = 0;
//        LinkedList<CallinMappingDeclaration> staticReplaces = new LinkedList<CallinMappingDeclaration>();
		for (int idx = 0; idx < methodMappings.length; idx++)
		{
			AbstractMethodMappingDeclaration methodMapping = methodMappings[idx];
			if(!methodMapping.ignoreFurtherInvestigation && methodMapping.isCallin())
			{
// CRIPPLE:
// 				  result &= createCallin((CallinMappingDeclaration) methodMapping);
                callinMappings[num++] = (CallinMappingDeclaration)methodMapping;
//                if (methodMapping.isStaticReplace())
//                	staticReplaces.add((CallinMappingDeclaration)methodMapping);
			}
		}
        System.arraycopy(
                callinMappings, 0,
                callinMappings = new CallinMappingDeclaration[num], 0,
                num);
        OTDynCallinBindingsAttribute.createOrMerge(this._role.getTeamModel(), this._role.getBaseTypeBinding().getRealClass().constantPoolName(), callinMappings);
// CRIPPLE:
//        if (staticReplaces.size() > 0) {
//        	CallinMappingDeclaration[] callins = new CallinMappingDeclaration[staticReplaces.size()];
//        	staticReplaces.toArray(callins);
//        	this._role.getTeamModel().addOrMergeAttribute(new StaticReplaceBindingsAttribute(callins));
//        }
	}

	// copied and slightly adjusted from old CallinImplementor:
	Expression getArgument(
			CallinMappingDeclaration	methodMapping,
			MethodDeclaration       	wrapperDeclaration,
			TypeBinding[]               implParameters,
			int		      				idx,
			final MethodSpec			sourceMethodSpec,
			char[]						roleVar)
	{
		final MethodSpec implementationMethodSpec = methodMapping.getImplementationMethodSpec();

	    Expression mappedArgExpr = null;
	    int        pos           = -1;
	    char[]     targetArgName = null;

		int generatedArgsLen = methodMapping.isReplaceCallin() ?
									MethodSignatureEnhancer.getEnhancingArgLen(WeavingScheme.OTDRE):
									0;
		final int srcIdx = idx-generatedArgsLen; // index into source-code signatures.

    	targetArgName = implementationMethodSpec.arguments[srcIdx].name;
    	// retrieve info collected during analyzeParameterMappings:
    	Pair<Expression,Integer> mapper = methodMapping.mappingExpressions[srcIdx];
		mappedArgExpr = mapper.first;
		if (mapper.second != null)
			pos = mapper.second.intValue();

	    if (mappedArgExpr != null) {
	    	if (methodMapping.baseMethodSpecs.length > 1) // multi-mappings need to copy mapped argument expressions:
	    		mappedArgExpr = copyExpression(mappedArgExpr, methodMapping.scope, methodMapping.compilationResult.getCompilationUnit());

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
			if (   RoleTypeBinding.isRoleWithoutExplicitAnchor(expectedType)
				&& TypeBinding.equalsEquals(roleType.getRealClass(), ((ReferenceBinding)expectedType).enclosingType()))
			{
				// expectedType is a role of the current role(=team),
				// use the role as the receiver for the lift call:
				receiver = gen.singleNameReference(roleVar);
			}
			if (sourceMethodSpec.hasSignature) {
				if (sourceMethodSpec.argNeedsTranslation(srcIdx)) {
					mappedArgExpr.tagReportedBaseclassDecapsulation();
					return Lifting.liftCall(methodMapping.scope,
											receiver != null ? receiver : ThisReference.implicitThis(),
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
			if (methodMapping.mappings != null && pos != -1 && liftExpr instanceof PotentialLiftExpression) {
				final int srcPos = pos;
				((PotentialLiftExpression)liftExpr).onLiftingRequired(new Runnable() {@Override
				public void run() {
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

	Expression copyExpression(Expression expression, Scope scope, ICompilationUnit cu) {
		if (cu == null) return expression; // FIXME: do we need a fallback when cu is built from model?
		final Parser parser = new Parser(scope.problemReporter(), false);
		char[] source = cu.getContents();
		return parser.parseExpression(source, expression.sourceStart, expression.sourceEnd - expression.sourceStart + 1, 
										scope.referenceCompilationUnit(), false /* record line separators */);
	}

	/**
	 * Main entry from Dependencies - teams.
	 * 
	 * Creates the dispatch methods.
	 */
	public void transformTeam(TeamModel aTeam) 
	{
		List<CallinMappingDeclaration> beforeMappings = new ArrayList<CallinMappingDeclaration>();
		List<CallinMappingDeclaration> replaceMappings = new ArrayList<CallinMappingDeclaration>();
		List<CallinMappingDeclaration> afterMappings = new ArrayList<CallinMappingDeclaration>();
		List<CallinMappingDeclaration> mappingsWithStaticBase = new ArrayList<CallinMappingDeclaration>();
		for (RoleModel role : aTeam.getRoles(false)) {
			TypeDeclaration roleDecl = role.getAst(); // FIXME(SH): this breaks incremental compilation: all roles must be present as AST!!
			if (roleDecl == null) continue; // FIXME(SH): check if this is OK
			if (roleDecl.callinCallouts != null) {
				for (AbstractMethodMappingDeclaration mappingDecl : roleDecl.callinCallouts) {
					if (mappingDecl.isCallin()) {
						CallinMappingDeclaration callinDecl = (CallinMappingDeclaration) mappingDecl;
						switch (callinDecl.callinModifier) {
							case TerminalTokens.TokenNamebefore: 	beforeMappings.add(callinDecl); 	break;
							case TerminalTokens.TokenNameafter: 	afterMappings.add(callinDecl); 		break;
							case TerminalTokens.TokenNamereplace: 	replaceMappings.add(callinDecl); 	break;
						}
						if (callinDecl.hasStaticBaseMethod())
							mappingsWithStaticBase.add(callinDecl);
					}
				}
			}
		}
//		if (beforeMappings.size() > 0)
//			generateDispatchMethod(OT_CALL_BEFORE,  false, false, beforeMappings, aTeam);
//		if (afterMappings.size() > 0)
//			generateDispatchMethod(OT_CALL_AFTER,   false, true, afterMappings, aTeam);
//		if (replaceMappings.size() > 0) {
//			generateDispatchMethod(OT_CALL_REPLACE, true,  false, replaceMappings, aTeam);
//			generateCallNext(replaceMappings, aTeam);
//		}
//		if (!mappingsWithStaticBase.isEmpty())
//			generateCallOrigStatic(mappingsWithStaticBase, aTeam);
	}

	private void generateDispatchMethod(char[] methodName, final boolean isReplace, final boolean isAfter, final List<CallinMappingDeclaration> callinDecls, final TeamModel aTeam) 
	{
		// FIXME(SH): once we know that Team has empty implementations (and checked cases involving team inheritance)
		// we probably want to avoid generating empty methods here.
		final TypeDeclaration teamDecl = aTeam.getAst();
		if (teamDecl == null) return;

		final AstGenerator gen = new AstGenerator(teamDecl);
		gen.replaceableEnclosingClass = teamDecl.binding;

		
		// public void _OT$callBefore   (IBoundBase2 base, 							int boundMethodId, int callinId, 	Object[] args)
		// public void _OT$callAfter	(IBoundBase2 base, 							int boundMethodId, int callinId, 	Object[] args, Object result)
		// public void _OT$callReplace	(IBoundBase2 base, Team[] teams, int index, 	int boundMethodId, int[] callinIds, Object[] args)
		int length = 4;
		if (isReplace)
			length = 6;
		else if (isAfter)
			length = 5;
		Argument[] arguments = new Argument[length]; 
		int a = 0;
		arguments[a++] 		= gen.argument(_BASE$, gen.qualifiedTypeReference(IOTConstants.ORG_OBJECTTEAMS_IBOUNDBASE2));
		if (isReplace)
			arguments[a++] 	= gen.argument(TEAMS, gen.qualifiedArrayTypeReference(IOTConstants.ORG_OBJECTTEAMS_ITEAM, 1));
		if (isReplace)
			arguments[a++] 	= gen.argument(INDEX, gen.typeReference(TypeBinding.INT));
		arguments[a++] 		=
			isReplace  	?	  gen.argument(CALLIN_ID, gen.createArrayTypeReference(TypeBinding.INT, 1))
						:	  gen.argument(CALLIN_ID, gen.typeReference(TypeBinding.INT));
		arguments[a++] 		= gen.argument(BOUND_METHOD_ID, gen.typeReference(TypeBinding.INT));
		arguments[a++] 		= gen.argument(ARGUMENTS, gen.qualifiedArrayTypeReference(TypeConstants.JAVA_LANG_OBJECT, 1));
		if (isAfter)
			arguments[a++]	= gen.argument(_OT_RESULT, gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT));
		
		TypeReference returnTypeRef = isReplace ? gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT) : gen.typeReference(TypeBinding.VOID);
		
		final MethodDeclaration callMethod = gen.method(teamDecl.compilationResult, AccPublic, returnTypeRef, methodName, arguments);
		callMethod.isMappingWrapper = AbstractMethodDeclaration.WrapperKind.CALLIN;
		
		AstEdit.addMethod(teamDecl, callMethod);

		MethodModel.addCallinFlag(callMethod, IOTConstants.CALLIN_FLAG_WRAPPER);
		callMethod.model._declaringMappings = callinDecls;
		
		MethodModel.getModel(callMethod).setStatementsGenerator(new AbstractStatementsGenerator() {

			@Override
			protected boolean generateStatements(AbstractMethodDeclaration methodDecl) {
				
				// into head of tryStats we generate local vars to be shared by case statements:
				List<Statement> tryStats = new ArrayList<Statement>();

				SwitchStatement switchStat = new SwitchStatement();
				switchStat.expression =
					isReplace 
					? gen.arrayReference(gen.singleNameReference(CALLIN_ID), gen.singleNameReference(INDEX))	// switch(callinId[index]) {  ...
					: gen.singleNameReference(CALLIN_ID);														// switch(callinId) { ...
				
				// statements for the body of the switchStatement:
				List<Statement> statements = new ArrayList<Statement>();

				// count callins:
				for (CallinMappingDeclaration callinDecl : callinDecls) 
					for (MethodSpec baseSpec : callinDecl.baseMethodSpecs)
						baseSpec.getCallinId(aTeam);
				int callinIdCount = teamDecl.getTeamModel().getCallinIdCount();
				// callinIds not handled here will be handled using a super-call.
				boolean[] handledCallinIds = new boolean[callinIdCount];
				// do we need to catch LiftingFailedException?
				boolean canLiftingFail = false;
				// one case block per callin mapping:
				for (CallinMappingDeclaration callinDecl : callinDecls) 
				{
					if (callinDecl.ignoreFurtherInvestigation)
						continue;
					TypeDeclaration roleDecl = callinDecl.scope.referenceType();
					if (roleDecl.ignoreFurtherInvestigation || RoleModel.isRoleWithBaseProblem(roleDecl))
						continue;
					if (!callinDecl.hasParsedParamMappings) // during reconcile we may not be interested in this level of detail (e.g., of a role file)
						continue;

					gen.retargetFrom(callinDecl);

					// one case label per bound base method:
					for (MethodSpec baseSpec : callinDecl.baseMethodSpecs) {
						int callinID = baseSpec.getCallinId(aTeam);
						statements.add(gen.caseStatement(gen.intLiteral(callinID)));				// case <baseMethod.callinId>: 
						handledCallinIds[callinID] = true;

						PredicateGenerator predGen = new PredicateGenerator(
															callinDecl.binding._declaringRoleClass,
															callinDecl.isReplaceCallin());
	
						TypeBinding baseReturn = baseSpec.resolvedType();
						boolean isStaticRoleMethod = callinDecl.getRoleMethod().isStatic();
						ReferenceBinding roleType = callinDecl.scope.enclosingSourceType();
						if (roleType.isGenericType()) // cannot handle generic role in this generated code
							roleType = (ReferenceBinding) callinDecl.scope.environment().convertToRawType(roleType, false);
						MethodBinding roleMethodBinding = callinDecl.getRoleMethod();
						
						boolean needLiftedRoleVar = !isStaticRoleMethod
												&& roleType.isCompatibleWith(roleMethodBinding.declaringClass);
						
						List<Statement> blockStatements = new ArrayList<Statement>();

						// do we need to expose _OT$result as result?
						char[] resultName = null;
						if (callinDecl.callinModifier == TerminalTokens.TokenNameafter
								&& (callinDecl.mappings != null || callinDecl.predicate != null) 
								&& baseReturn != TypeBinding.VOID) {
							resultName = RESULT;
							callinDecl.resultVar = gen.localBaseVariable(RESULT, baseReturn,							//   BaseReturnType result = (BaseReturnType)_OT$result; 
									  				gen.createCastOrUnboxing(gen.singleNameReference(_OT_RESULT), baseReturn, true/*baseAccess*/));
							blockStatements.add(callinDecl.resultVar);
						}
						// expose casted _base$ as "base":
						blockStatements.add(gen.localVariable(IOTConstants.BASE,
													gen.alienScopeTypeReference(gen.baseTypeReference(roleType.baseclass()),callinDecl.scope), 
													gen.castExpression(gen.baseNameReference(_BASE$), 
															gen.alienScopeTypeReference(gen.baseTypeReference(roleType.baseclass()),callinDecl.scope), 
															CastExpression.RAW)));

						// -------------- base predicate check -------
						boolean hasBasePredicate = false;
				        for (MethodSpec baseMethodSpec : callinDecl.baseMethodSpecs) { // FIXME: check this inner loop, outer already loops over baseMethods!!
				        	char[] resultName2 = null;
				        	if (   callinDecl.callinModifier == TerminalTokens.TokenNameafter
				    			&& baseMethodSpec.resolvedType() != TypeBinding.VOID)
				        	{
								resultName2 = IOTConstants.RESULT;
				        	}
				        	// FIXME(SH): only call predidate for the current base method (from BoundMethodID?)
							Statement predicateCheck = predGen.createBasePredicateCheck(
									callinDecl, baseMethodSpec, resultName2, gen);
							if (predicateCheck != null) {
								blockStatements.add(predicateCheck);												//   if (!base$when(baseArg,...)) throw new LiftingVetoException();
								hasBasePredicate = true;
							}
				        }
				        // below this line we are "inside" the callin execution:
				        blockStatements.add(gen.assignment(gen.singleNameReference(CallinImplementor.OLD_IS_EXECUTING), // _OT$oldIsExecutingCallin = _OT$setExecutingCallin(true); // local declared at top level
				        								   gen.messageSend(
			        												gen.thisReference(),
			        												IOTConstants.SET_EXECUTING_CALLIN,
			        												new Expression[]{ gen.booleanLiteral(true) })));

				        // ----------- receiver for role method call: -----------
				        Expression receiver;
						char[] roleVar = null;
						if (!isStaticRoleMethod) {
							if (needLiftedRoleVar) {
	
								canLiftingFail |= checkLiftingProblem(teamDecl, callinDecl, roleType);
	
								roleVar = (LOCAL_ROLE+statements.size()).toCharArray();
								TypeReference roleTypeReference = gen.roleTypeReference(teamDecl.getTeamModel().getTThis(), roleType, 0);
								blockStatements.add(gen.localVariable(roleVar, 										//   RoleType local$n = this._OT$liftToRoleType((BaseType)base);
														gen.alienScopeTypeReference(roleTypeReference, callinDecl.scope),
										ClassFileConstants.AccFinal,
										Lifting.liftCall(callMethod.scope,
														 gen.thisReference(),
														 gen.baseNameReference(IOTConstants.BASE),
														 callMethod.scope.getType(IOTConstants.ORG_OBJECTTEAMS_IBOUNDBASE2, 3),
														 roleType,
														 false,
														 gen)));
								receiver = gen.thislikeNameReference(roleVar);
								// private receiver needs to be casted to the class.
							} else {
								// method is from role's enclosing team
								receiver = gen.qualifiedThisReference(TeamModel.strengthenEnclosing(teamDecl.binding, roleMethodBinding.declaringClass));
							}
						} else {
							receiver = gen.singleNameReference(callinDecl.getRoleMethod().declaringClass.sourceName());
						}
						
						int baseArgOffset = 0;
						if (baseSpec.isCallin()) baseArgOffset+=MethodSignatureEnhancer.getEnhancingArgLen(WeavingScheme.OTDRE);
						if (baseSpec.isStatic() && baseSpec.getDeclaringClass().isRole()) baseArgOffset+=2;
						// unpack arguments to be used by parameter mappings and base predicate:
						// ArgTypeN argn = args[n]
						if (callinDecl.mappings != null || (hasBasePredicate && baseSpec.arguments != null)) {
							TypeBinding[] baseParams = baseSpec.resolvedParameters();
							for (int i=0; i<baseSpec.arguments.length; i++) {										//   BaseType baseArg = castAndOrUnbox(arguments[n]);
								Argument baseArg = baseSpec.arguments[i];
								Expression rawArg = gen.arrayReference(gen.singleNameReference(ARGUMENTS), i+baseArgOffset);
								Expression init = rawArg;
								if (!baseParams[i].isTypeVariable())
									init = gen.createCastOrUnboxing(rawArg, baseParams[i], callinDecl.scope);
								LocalDeclaration baseArgLocal = gen.localVariable(baseArg.name,
																	gen.alienScopeTypeReference(baseArg.type, callinDecl.scope),
																	init);
								baseArgLocal.modifiers |= (baseArg.modifiers & ClassFileConstants.AccFinal);
								if (hasBasePredicate) {
									// add to front so it is already available for the base predicate check:
									blockStatements.add(i, baseArgLocal);
								} else {
									// otherwise give it a chance for expressions/types that depend on the role instance
									baseArgLocal.initialization = new PotentialRoleReceiverExpression(
																	  init,
																	  roleVar,
																	  gen.typeReference(roleType));
									blockStatements.add(baseArgLocal);
								}
							}
						}
	
						// -- assemble arguments:
						TypeBinding[] roleParams = callinDecl.roleMethodSpec.resolvedParameters();
						Expression[] callArgs = new Expression [roleParams.length + (isReplace ? MethodSignatureEnhancer.getEnhancingArgLen(WeavingScheme.OTDRE) : 0)];
						int idx = 0;
						if (isReplace)
							for (char[] argName : REPLACE_ARG_NAMES)
								callArgs[idx++] = gen.singleNameReference(argName);									//    prepare: base, teams, boundMethodId, callinIds, index, arguments ...
						
						// prepare parameter mappings:
						callinDecl.traverse(new ReplaceResultReferenceVisitor(callinDecl), callinDecl.scope.classScope());
	
						boolean hasArgError = false;
						for (int i=0; i<roleParams.length; i++) {
							Expression arg;
							TypeBinding roleParam = roleParams[i];
							if (roleParam.isTypeVariable()) {
								TypeVariableBinding tvb = (TypeVariableBinding) roleParam;
								if (tvb.declaringElement instanceof MethodBinding) {
									if (TypeBinding.equalsEquals(((MethodBinding)tvb.declaringElement).declaringClass, roleType))
										// don't use type variable of target method, see test4140_callinReplaceCompatibility10s()
										roleParam = roleParam.erasure();
								}
							}
							TypeReference localTypeRef = null;
							if (callinDecl.mappings == null) {
								// ------------ unmapped arguments --------------
								arg = gen.arrayReference(gen.singleNameReference(ARGUMENTS), i+baseArgOffset);					//    prepare: somePreparation(arguments[i])
								TypeBinding baseArgType = baseSpec.resolvedParameters()[i];
								if (roleParam.isBaseType()) {
									// this includes intermediate cast to boxed type:
									arg = gen.createUnboxing(arg, (BaseTypeBinding)roleParam);
								} else if (baseArgType.isBaseType()) {
									// Object -> BoxingType
									arg = gen.castExpression(arg,
															 gen.qualifiedTypeReference(AstGenerator.boxTypeName((BaseTypeBinding) baseArgType)),
															 CastExpression.RAW);
								} else {
									// Object -> MyBaseClass
									ReferenceBinding baseclass = roleType.baseclass();
									if (baseclass instanceof DependentTypeBinding && baseArgType instanceof ReferenceBinding)
										baseArgType = RoleTypeCreator.maybeInstantiateFromPlayedBy(callinDecl.scope, (ReferenceBinding)baseArgType);
									arg = gen.castExpression(arg,
															 gen.alienScopeTypeReference(
																	gen.typeReference(baseArgType),
																	callinDecl.scope),
															 CastExpression.DO_WRAP);
									if (!roleParam.leafComponentType().isBaseType()
											&& PotentialLiftExpression.isLiftingRequired(callinDecl.scope, roleParam, baseArgType, arg)) {
										// lift?(MyBaseClass)
										Reference liftReceiver = null; // default: let gen find the team
										if (roleType.isTeam() && TypeBinding.equalsEquals(roleParam.enclosingType(), roleType))
											liftReceiver = gen.singleNameReference(roleVar); // lift to inner role
										arg = gen.potentialLift(liftReceiver, arg, roleParam, isReplace/*reversible*/);
										localTypeRef = gen.typeReference(roleParam);
										canLiftingFail |= checkLiftingProblem(teamDecl, callinDecl, (ReferenceBinding)roleParam.leafComponentType());
									}
								}
								if (localTypeRef == null)
									localTypeRef = gen.baseclassReference(baseArgType); // unless lifting was required above
							} else {
								// ------------ mapped arguments --------------
				 				if (roleParam.isTypeVariable() && ((TypeVariableBinding)roleParam).declaringElement instanceof CallinCalloutBinding)
				 					localTypeRef = gen.typeReference(roleParam.erasure()); // cannot explicitly mention this TVB
				 				else
				 					localTypeRef = gen.typeReference(roleParam);
								
				 				arg = getArgument(callinDecl, 														//    prepare:  <mappedArg<n>>
												  (MethodDeclaration) methodDecl,
												  callinDecl.getRoleMethod().parameters, 
												  i+idx,
												  baseSpec,
												  roleVar);
								if (arg == null) {
									hasArgError = true;
									continue; // keep going to find problems with other args, too.
								}
								if (Lifting.isLiftToMethodCall(arg))
									canLiftingFail |= checkLiftingProblem(teamDecl, callinDecl, roleType);
								boolean isBaseReference = arg instanceof SingleNameReference 
														  && CharOperation.equals(((SingleNameReference)arg).token, IOTConstants.BASE);
								if (needLiftedRoleVar)
									arg = new PotentialRoleReceiverExpression(arg, roleVar, gen.typeReference(roleType.getRealClass()));
								// mapped expression may require casting: "base" reference has static type IBoundBase2
								if (isBaseReference)
									arg = gen.castExpression(arg, gen.typeReference(roleParam), CastExpression.RAW);
							}
			 				char[] localName = (OT_LOCAL+i).toCharArray();											//    RoleParamType _OT$local$n = preparedArg<n>;
							blockStatements.add(gen.localVariable(localName, gen.alienScopeTypeReference(localTypeRef, callinDecl.scope), arg));
							callArgs[i+idx] = gen.singleNameReference(localName);									//    prepare: ... _OT$local$ ...
	
						}
						if (hasArgError)
							continue;

						// -- role side predicate:
						Expression[] predicateArgs = isReplace 
								? MethodSignatureEnhancer.retrenchBasecallArguments(callArgs, true, WeavingScheme.OTDRE)
								: callArgs;
						predicateArgs = maybeAddResultReference(callinDecl, predicateArgs, resultName, gen);
				        Statement rolePredicateCheck = predGen.createPredicateCheck(								//    if (!when(callArgs)) throw new LiftingVetoException();
				        		callinDecl,
				        		callinDecl.scope.referenceType(),
								receiver,
								predicateArgs,
								callArgs,
								gen);
						if (rolePredicateCheck != null)
				        	// predicateCheck(_OT$role)
				        	blockStatements.add(rolePredicateCheck);
	
						// -- assemble the method call:																//    local$n.roleMethod((ArgType0)args[0], .. (ArgTypeN)args[n]);
						boolean lhsResolvesToTeamMethod = TypeBinding.equalsEquals(callinDecl.getRoleMethod().declaringClass, roleType.enclosingType()); // TODO(SH): more levels
						MessageSend roleMethodCall = (callinDecl.getRoleMethod().isPrivate() && !lhsResolvesToTeamMethod) 
							? new PrivateRoleMethodCall(receiver, callinDecl.roleMethodSpec.selector, callArgs, false/*c-t-f*/, 
													    callinDecl.scope, roleType, callinDecl.getRoleMethod(), gen)
							: gen.messageSend(receiver, callinDecl.roleMethodSpec.selector, callArgs);
						roleMethodCall.isGenerated = true; // for PrivateRoleMethodCall
						roleMethodCall.isPushedOutRoleMethodCall = true;
						
						// -- post processing:
						Statement[] messageSendStatements;
						if (isReplace) {
							Expression result = roleMethodCall;
							if (baseSpec.returnNeedsTranslation) {
								// lowering:
								TypeBinding[]/*role,base*/ returnTypes = getReturnTypes(callinDecl, 0);
								//   who is responsible for lowering: the team or the current role?
								Expression lowerReceiver = (isRoleOfCurrentRole(roleType, returnTypes[0]))
										? gen.singleNameReference(roleVar)
										: genTeamThis(gen, returnTypes[0]);
								result = new Lowering().lowerExpression(methodDecl.scope, result, returnTypes[0], returnTypes[1],
										lowerReceiver, true/*needNullCheck*/, true/*delayedResolve*/);
							}
							// possibly convert using result mapping
							callinDecl.checkResultMapping();
							boolean isResultBoxed = baseReturn.isBaseType() && baseReturn != TypeBinding.VOID;
							if (   callinDecl.mappings != null
								&& callinDecl.isResultMapped)
							{
								if (isResultBoxed)
									result = gen.createUnboxing(result, (BaseTypeBinding) baseReturn);
								Expression mappedResult = callinDecl.getResultExpression(baseSpec, isResultBoxed, gen/*stepOverGen*/);
								if (!isStaticRoleMethod) 
									mappedResult = new PotentialRoleReceiverExpression(
															mappedResult,
															roleVar,
															gen.typeReference(roleType.getRealClass()));
								messageSendStatements = new Statement[] {
									callinDecl.resultVar =
										gen.localVariable(IOTConstants.RESULT, baseReturn, 							//   result = (Type)role.roleMethod(args);
														  gen.castExpression(result, gen.typeReference(baseReturn), CastExpression.RAW)),
														  // cast because role return might be generalized
										gen.returnStatement(mappedResult) 											//   return mappedResult(result);
								};
							} else {
								if (isResultBoxed) {																// $if_need_result_unboxing$
									messageSendStatements = new Statement[] {
										gen.localVariable(IOTConstants.OT_RESULT, 									//   Object _OT$result = role.roleMethod(args);
														  gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT), 
														  result),
										CallinImplementor.genResultNotProvidedCheck(								//	 if (_OT$result == null)
														  teamDecl.binding.readableName(), 							//		throw new ResultNotProvidedException(..)
														  roleType.readableName(),
														  roleMethodBinding, 
														  roleType.baseclass(), 
														  baseSpec, 
														  gen),
										gen.returnStatement(gen.singleNameReference(IOTConstants.OT_RESULT))		//  return _OT$result;
									};
								} else {																			// $endif$
									messageSendStatements = new Statement[] { gen.returnStatement(result) };		//   return role.roleMethod(args);
								}
							}
						} else {
							messageSendStatements = new Statement[] {
									roleMethodCall,																	//   role.roleMethod(args);
									gen.breakStatement()															//   break;
							};
						}
						// assemble:
						//		try { roleMessageSend(); }
						//		catch(Exception _OT$caughtException) { throw new SneakyException(_OT$caughtException); }
						//		finally { _OT$setExecutingCallin(_OT$oldIsExecutingCallin); } 
						blockStatements.add(protectRoleMethodCall(messageSendStatements, roleMethodBinding, gen));
						statements.add(gen.block(blockStatements.toArray(new Statement[blockStatements.size()])));
						// collectively report the problem(s)
						if (canLiftingFail && callinDecl.rolesWithLiftingProblem != null)
							for (Map.Entry<ReferenceBinding, Integer> entry : callinDecl.rolesWithLiftingProblem.entrySet())
								callinDecl.scope.problemReporter().callinDespiteLiftingProblem(entry.getKey(), entry.getValue(), callinDecl);
					}
				} // END for (CallinMappingDeclaration callinDecl : callinDecls) 
				
				gen.retargetFrom(teamDecl);
				
				boolean needSuperCall = false;
				// do we have a relevant super team, which possibly defines more callins?
				ReferenceBinding superTeam = aTeam.getBinding().superclass();
				if (superTeam != null && superTeam.isTeam() && superTeam.id != IOTConstants.T_OrgObjectTeamsTeam) {
					// callinIds to be handled by super call?
					for (int i=0; i < callinIdCount; i++)
						if (!handledCallinIds[i]) {
							statements.add(gen.caseStatement(gen.intLiteral(i)));									// case callinIdOfSuper:
							needSuperCall = true;
						}
					if (!isReplace)
						needSuperCall = true;
					// a super call might become necessary after the fact when this dispatch method
					// is copy-inherited to a tsub-team, because the tsub-team may have a super
					// with more callins, see test1111_roleInheritsCallinFromTsupers1.
					// TODO: can we safely handle this for the replace-case, too??
					// (replace needs to "return _OT$callNext();" in the default branch, see below).
					// See https://bugs.eclipse.org/433123
				}
				if (needSuperCall) {
					if (!isReplace)
						statements.add(gen.caseStatement(null)); // default label
					char[]   selector;				char[][] argNames;
					if (isReplace) {
						selector = OT_CALL_REPLACE;	argNames = REPLACE_ARG_NAMES;
					} else if (isAfter) {
						selector = OT_CALL_AFTER;	argNames = AFTER_ARG_NAMES;
					} else {
						selector = OT_CALL_BEFORE;	argNames = BEFORE_ARG_NAMES;
					}
					Expression[] superCallArgs = new Expression[argNames.length];
					for (int idx=0; idx < argNames.length; idx++)
						superCallArgs[idx] = gen.singleNameReference(argNames[idx]);
					// if we have a tsuper team which a corresponding dispatch method that one takes precedence:
					MessageSend superCall = aTeam.hasTSuperTeamMethod(selector)
							? gen.tsuperMessageSend(gen.thisReference(), selector, superCallArgs)
							: gen.messageSend(gen.superReference(), selector, superCallArgs);
					if (isReplace)
						statements.add(gen.returnStatement(superCall));											//    return super._OT$callReplace(..);
					else
						statements.add(superCall);																//    super._OT$callBefore/After(..);
				}

				Statement catchStatement1 = gen.emptyStatement();
				Statement catchStatement2 = gen.emptyStatement();
				if (isReplace) { 

					// default: callNext:
					Expression[] callArgs = new Expression[REPLACE_ARG_NAMES.length+2];
					for (int idx=0; idx < REPLACE_ARG_NAMES.length; idx++)
						callArgs[idx] = gen.singleNameReference(REPLACE_ARG_NAMES[idx]);
					callArgs[callArgs.length-2] = gen.nullLiteral(); // no explicit baseCallArguments
					callArgs[callArgs.length-1] = gen.intLiteral(0); // not a base call  
					statements.add(gen.caseStatement(null)); 													// default:
					statements.add(gen.returnStatement(															//    return _OT$callNext(..);
										gen.messageSend(
											gen.qualifiedThisReference(aTeam.getBinding()), 
											OT_CALL_NEXT, 
											callArgs)));
					catchStatement1 = gen.returnStatement(
										gen.messageSend(
											gen.qualifiedThisReference(aTeam.getBinding()), 
											OT_CALL_NEXT, 
											callArgs));
					catchStatement2 = gen.returnStatement(
										gen.messageSend(
											gen.qualifiedThisReference(aTeam.getBinding()), 
											OT_CALL_NEXT, 
											callArgs));
				}
				
				// ==== overall assembly: ====
				switchStat.statements = statements.toArray(new Statement[statements.size()]);
				Argument[] exceptionArguments;
				Statement[][] exceptionStatementss;
				if (canLiftingFail) {
					exceptionArguments = new Argument[] {
						gen.argument("ex".toCharArray(),  //$NON-NLS-1$
									 gen.qualifiedTypeReference(IOTConstants.ORG_OBJECTTEAMS_LIFTING_VETO)),
						gen.argument("ex".toCharArray(),  //$NON-NLS-1$
								 gen.qualifiedTypeReference(IOTConstants.O_O_LIFTING_FAILED_EXCEPTION))
					};
					exceptionStatementss = new Statement[][]{{catchStatement1},{catchStatement2}};
				} else {
					exceptionArguments = new Argument[] {
							gen.argument("ex".toCharArray(),  //$NON-NLS-1$
										 gen.qualifiedTypeReference(IOTConstants.ORG_OBJECTTEAMS_LIFTING_VETO))
					};
					exceptionStatementss = new Statement[][]{{catchStatement1}};				
				}
				tryStats.add(switchStat);

				LocalDeclaration flagVariable = gen.localVariable(CallinImplementor.OLD_IS_EXECUTING,
														gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_BOOLEAN),
														gen.nullLiteral());
				Statement resetFlag = gen.ifStatement(gen.nonNullCheck(gen.singleNameReference(CallinImplementor.OLD_IS_EXECUTING)),
										gen.messageSend(gen.thisReference(),
														IOTConstants.SET_EXECUTING_CALLIN,
														new Expression[]{ gen.singleNameReference(CallinImplementor.OLD_IS_EXECUTING) }));
				methodDecl.statements = new Statement[] {
							flagVariable,
							gen.tryStatement(
								tryStats.toArray(new Statement[tryStats.size()]),
								// expected exception is ignored, do nothing (before/after) or proceed to callNext (replace)
								exceptionArguments,
								exceptionStatementss,
								new Statement[] {resetFlag})
						};
				methodDecl.hasParsedStatements = true;
				return true;
			}

		});
	}

	/**
	 * Assemble message send arguments plus perhaps a result reference to
	 * yield argument expressions for a predicate call.
	 * (From old CallinImplementor).
	 */
	Expression[] maybeAddResultReference(CallinMappingDeclaration callinBindingDeclaration,
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
	
	private void generateCallNext(final List<CallinMappingDeclaration> callinDecls, final TeamModel aTeam) {
		// public Object _OT$callNext(IBoundBase2 baze, Team[] teams, int idx, int[] callinIds, int boundMethodId, Object[] args, Object[] baseCallArgs) 
		final TypeDeclaration teamDecl = aTeam.getAst();
		if (teamDecl == null) return;
		final AstGenerator gen = new AstGenerator(teamDecl);
		Argument[] args = new Argument[] {
				gen.argument(IOTConstants.BASE, gen.qualifiedTypeReference(IOTConstants.ORG_OBJECTTEAMS_IBOUNDBASE2)),
				gen.argument(TEAMS, 			gen.qualifiedArrayTypeReference(IOTConstants.ORG_OBJECTTEAMS_ITEAM, 1)),
				gen.argument(INDEX, 			gen.typeReference(TypeBinding.INT)),
				gen.argument(CALLIN_ID, 		gen.createArrayTypeReference(TypeBinding.INT, 1)),
				gen.argument(BOUND_METHOD_ID, 	gen.typeReference(TypeBinding.INT)),
				gen.argument(ARGUMENTS, 		gen.qualifiedArrayTypeReference(TypeConstants.JAVA_LANG_OBJECT, 1)),
				gen.argument(BASE_CALL_ARGS, 	gen.qualifiedArrayTypeReference(TypeConstants.JAVA_LANG_OBJECT, 1)),
				gen.argument(BASE_CALL_FLAGS,	gen.typeReference(TypeBinding.INT))
		};
		// super call directly passes all these args through:
		Expression[] superArgs = new Expression[args.length];
		for (int i=0; i<args.length;i++)
			superArgs[i] = gen.singleNameReference(args[i].name);
		
		MethodDeclaration decl = gen.method(teamDecl.compilationResult,
											AccPublic,
											gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT),
											OT_CALL_NEXT,
											args);
		
		// the payload: reverse parameter mappings:
		SwitchStatement swStat = new SwitchStatement();
		swStat.expression = gen.arrayReference(gen.singleNameReference(CALLIN_ID), gen.singleNameReference(INDEX));	// switch(callinId[index]) { ...
		List<Statement> swStatements = new ArrayList<Statement>(); 
		char[] resultVar = RESULT;
		for (CallinMappingDeclaration mapping : callinDecls) {
			int nLabels = 0;
			for (MethodSpec baseSpec : mapping.baseMethodSpecs) {
				List<Statement> caseBlockStats = new ArrayList<Statement>();
				caseBlockStats.add(gen.caseStatement(gen.intLiteral(baseSpec.getCallinId(aTeam))));					// case baseSpecCallinId:
				int nRoleArgs = mapping.getRoleMethod().getSourceParamLength();
				TypeBinding[] roleParams = mapping.getRoleMethod().getSourceParameters();
				for (int i=0; i<roleParams.length; i++)
					if (roleParams[i].isRole() && TeamModel.isTeamContainingRole(teamDecl.binding, (ReferenceBinding) roleParams[i]))
						roleParams[i] = TeamModel.strengthenRoleType(teamDecl.binding, roleParams[i]);
	
				List<Statement> repackingStats = new ArrayList<Statement>();
				
				if (mapping.positions != null) {
					int[] poss = mapping.positions;
					nLabels = caseBlockStats.size();
					int argOffset = 0; // some methods have their real arguments at an offset
					if (baseSpec.isCallin())
						argOffset += 6;
					if (baseSpec.isStatic() && baseSpec.getDeclaringClass().isRole())
						argOffset += 2;
					for (int i=0; i<poss.length; i++)
						// arguments[basepos] = baseCallArguments[i]
						if (poss[i] > 0) {
							// FIXME(SH): this is cheating: should obtain translation info from actual 
							// parameter mapping (see cast in test432_expressionInReplaceParameterMapping11)
							TypeBinding roleSideParameter = roleParams[i];
							TypeBinding baseSideParameter = baseSpec.resolvedParameters()[poss[i]-1];
							Expression roleSideArgument = gen.arrayReference(gen.singleNameReference(BASE_CALL_ARGS), i);//   ... baseCallArguments[i] ...
							if (TypeBinding.notEquals(roleSideParameter, baseSideParameter))
								roleSideArgument = gen.resolvedCastExpression(roleSideArgument, roleSideParameter, CastExpression.RAW);
							TypeBinding roleSideLeaf = roleSideParameter.leafComponentType();
							TypeBinding baseSideLeaf = baseSideParameter.leafComponentType();
							if (   roleSideLeaf.isRole() 
								&& ((ReferenceBinding)roleSideLeaf).baseclass().isCompatibleWith(baseSideLeaf))
								roleSideArgument = new PotentialLowerExpression(roleSideArgument, baseSideParameter, gen.thisReference());
							repackingStats.add(gen.assignment(gen.arrayReference(gen.singleNameReference(ARGUMENTS), 	//   arguments[p] = baseCallArguments[i];
									                                             poss[i]-1+argOffset), // 0 represents result
															  roleSideArgument));
						}
				} else if (nRoleArgs > 0) {
					for (int i=0; i<nRoleArgs; i++) {
						Expression basecallArg = gen.arrayReference(gen.singleNameReference(BASE_CALL_ARGS), i);
						if (baseSpec.argNeedsTranslation(i)) {
							basecallArg = new PotentialLowerExpression(gen.castExpression(basecallArg,
																				 			gen.typeReference(roleParams[i]),
																				 			CastExpression.RAW),
																	   baseSpec.resolvedParameters()[i],
																	   gen.qualifiedThisReference(teamDecl.binding));
						}
						repackingStats.add(gen.assignment(gen.arrayReference(gen.singleNameReference(ARGUMENTS), i),	//    arguments[i] = lower?(baseCallArguments[i])
								  		   				  basecallArg));
					}
				}
				caseBlockStats.add(gen.ifStatement(gen.nullCheck(gen.singleNameReference(BASE_CALL_ARGS)),				//    if (baseCallArgs == null) {} { arguments[i] = ...; ... } 
												   gen.emptyStatement(),
												   gen.block(repackingStats.toArray(new Statement[repackingStats.size()]))));
	
				Expression result = genSuperCallNext(gen, teamDecl.binding, superArgs);									//    return cast+lift?(super._OT$callNext(..));
				if (baseSpec.returnNeedsTranslation) {
					// lifting:
					TypeBinding[]/*role,base*/ returnTypes = getReturnTypes(mapping, 0);
					//   who is responsible for lifting: the team or the current role?
					ReferenceBinding currentRole = mapping.scope.enclosingReceiverType();
					Expression liftReceiver = (isRoleOfCurrentRole(currentRole, returnTypes[0]))
						? Lifting.liftCall(mapping.scope,
											gen.thisReference(),
											gen.castExpression(gen.singleNameReference(IOTConstants.BASE), gen.typeReference(currentRole.baseclass()), CastExpression.RAW),
											currentRole.baseclass(), currentRole, false)
									// TODO: might want to extend the signature of callNext to pass the current role to avoid this lifting?
						: genTeamThis(gen, returnTypes[0]);
					resultVar = CharOperation.append(resultVar, '$');
					caseBlockStats.add(gen.localVariable(resultVar, gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT), result));
					caseBlockStats.add(gen.ifStatement(gen.equalExpression(gen.singleNameReference(BASE_CALL_FLAGS), gen.intLiteral(0), OperatorIds.NOT_EQUAL), 
							gen.assignment(gen.singleNameReference(resultVar), 
											Lifting.liftCall(mapping.scope,
											  liftReceiver,
											  gen.castExpression(gen.singleNameReference(resultVar),
													  			 gen.baseclassReference(returnTypes[1]),
													  			 CastExpression.RAW),
											  returnTypes[1],
											  returnTypes[0],
											  false,
											  gen)),
							null));
					result = gen.singleNameReference(resultVar);
				}
				caseBlockStats.add(gen.returnStatement(result));
				
				if (caseBlockStats.size() > nLabels) { // any action added ?
					swStatements.addAll(caseBlockStats);
				}
			}																											// } // end-switch
		}
		if (swStatements.size() == 0)
			return; // don't add useless method
		
		swStat.statements = swStatements.toArray(new Statement[swStatements.size()]);
		decl.statements = new Statement[] {
			swStat,
			gen.returnStatement(genSuperCallNext(gen, teamDecl.binding, superArgs)) // delegate with unchanged arguments/return
		};
		decl.hasParsedStatements = true;
		AstEdit.addMethod(teamDecl, decl);
	}

	private Expression genSuperCallNext(AstGenerator gen, SourceTypeBinding binding, Expression[] superArgs) {
		if (binding.superclass.isTeam())
			return gen.messageSend(gen.superReference(), OT_CALL_NEXT, superArgs);
		// no super-*team* so call the static variant:
		Expression[] argsCopy = Arrays.copyOf(superArgs, superArgs.length);
		argsCopy[superArgs.length-1] = gen.intLiteral(0); // not a base call
		return gen.messageSend(gen.qualifiedNameReference(IOTConstants.ORG_OBJECTTEAMS_TEAM), OT_TERMINAL_CALL_NEXT, argsCopy);
	}

	Reference genTeamThis(AstGenerator gen, TypeBinding type) {
		TypeBinding leaf = type.leafComponentType();
		if (leaf instanceof ReferenceBinding) {
			ReferenceBinding teamBinding = ((ReferenceBinding) leaf).enclosingType();
			if (teamBinding != null)
				return gen.qualifiedThisReference(teamBinding);
		}
		return gen.thisReference();
	}

	TypeBinding[] getReturnTypes(CallinMappingDeclaration mapping, int i) {
		TypeBinding baseReturn = mapping.baseMethodSpecs[i].resolvedType();
		TypeBinding roleReturn = mapping.roleMethodSpec.resolvedType();
		if (roleReturn.isTypeVariable())
			roleReturn = ((TypeVariableBinding)roleReturn).firstBound;
		return new TypeBinding[]{roleReturn, baseReturn};
	}

	/** Convert custom exceptions into SneakyException as to bypass checking by the compiler. */
	TryStatement protectRoleMethodCall(Statement[] statements, MethodBinding roleMethod, AstGenerator gen) {
		Argument catch1Arg = gen.argument(CATCH_ARG, gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_RUNTIMEEXCEPTION)); // incl. LiftingVetoException
		Statement[] catch1Stat = new Statement[] {
				gen.throwStatement(gen.singleNameReference(CATCH_ARG)) // rethrow as-is
		};
		Argument catch2Arg = gen.argument(CATCH_ARG, gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_EXCEPTION));
		Statement[] catch2Stat = new Statement[] {
				gen.throwStatement(gen.allocation(
						gen.qualifiedTypeReference(IOTConstants.SNEAKY_EXCEPTION),
						new Expression[] { gen.singleNameReference(CATCH_ARG) }
				))
		};
		return gen.tryCatch(
				statements,
				new Argument[] {catch1Arg, catch2Arg}, 
				new Statement[][] {catch1Stat, catch2Stat});
	}

	private void generateCallOrigStatic(List<CallinMappingDeclaration> callinDecls, TeamModel aTeam) {
		// public Object _OT$callOrigStatic(int callinId, int boundMethodId, Object[] args)
		// this team method delegates to the corresponding _OT$callOrigStatic(int,Object[])
		// of the appropriate base classes.
		final TypeDeclaration teamDecl = aTeam.getAst();
		if (teamDecl == null) return;
		int idxOfExisting = -1;
		AbstractMethodDeclaration[] teamMethods = teamDecl.methods;
		if (teamMethods != null) {
			for (int i = 0; i < teamMethods.length; i++) {
				if (CharOperation.equals(OT_CALL_ORIG_STATIC, teamMethods[i].selector) && teamMethods[i] instanceof TeamMethodGenerator.CopiedTeamMethod) {
					idxOfExisting = i;
					break;
				}
			}
		}
		final AstGenerator gen = new AstGenerator(teamDecl);
		Argument[] args = new Argument[] {
				gen.argument(CALLIN_ID, 		gen.typeReference(TypeBinding.INT)),
				gen.argument(BOUND_METHOD_ID, 	gen.typeReference(TypeBinding.INT)),
				gen.argument(ARGUMENTS, 		gen.qualifiedArrayTypeReference(TypeConstants.JAVA_LANG_OBJECT, 1))
		};
		Expression[] passThroughArgs = new Expression[] {
				gen.singleNameReference(BOUND_METHOD_ID),
				gen.singleNameReference(ARGUMENTS)
		};
		MethodDeclaration decl = gen.method(teamDecl.compilationResult,
											AccPublic,
											gen.qualifiedTypeReference(TypeConstants.JAVA_LANG_OBJECT),
											OT_CALL_ORIG_STATIC,
											args);
		
		SwitchStatement swStat = new SwitchStatement();
		swStat.expression = gen.singleNameReference(CALLIN_ID);											// switch(callinId) { ...
		List<Statement> swStatements = new ArrayList<Statement>(); 
		for (CallinMappingDeclaration mapping : callinDecls) {
			for (MethodSpec baseSpec : mapping.baseMethodSpecs) {
				MethodBinding baseMethod = baseSpec.resolvedMethod;
				if (baseMethod.isStatic()) {
					ReferenceBinding baseClass = mapping.scope.enclosingReceiverType().baseclass();
					swStatements.add(gen.caseStatement(gen.intLiteral(baseSpec.getCallinId(aTeam))));		// case baseSpecCallinId:
					Expression result = gen.fakeMessageSend(gen.baseTypeReference(baseClass),				// 		return BaseClass._OT$callOrigStatic(boundMethodId, args);
															OT_CALL_ORIG_STATIC, 
															passThroughArgs,
															baseMethod.declaringClass,
															mapping.scope.getJavaLangObject());
					swStatements.add(gen.returnStatement(result));
				}
			}
		}																								// } // end-switch
		if (swStatements.size() == 0)
			return; // don't add useless method
		
		swStat.statements = swStatements.toArray(new Statement[swStatements.size()]);
		decl.statements = new Statement[] {
			swStat,
			gen.returnStatement(gen.nullLiteral()) // shouldn't happen
		};
		decl.hasParsedStatements = true;
		if (teamMethods != null && idxOfExisting > -1) {
			teamMethods[idxOfExisting] = decl; // directly replace
			teamDecl.binding.resolveGeneratedMethod(decl, false, null, false);
		} else {
			AstEdit.addMethod(teamDecl, decl);
		}
	}

	boolean checkLiftingProblem(TypeDeclaration teamDecl, CallinMappingDeclaration callinDecl, ReferenceBinding roleType) {
		int iProblem = teamDecl.getTeamModel().canLiftingFail(roleType);
		if (iProblem != 0) {
			callinDecl.addRoleLiftingProblem(roleType, iProblem);
			return true;
		}
		return false;
	}

	boolean isRoleOfCurrentRole(ReferenceBinding currentRole, TypeBinding type) {
		TypeBinding leafType = type.leafComponentType();
		if (leafType.isRole()) {
			return currentRole.erasure().isCompatibleWith(leafType.enclosingType().erasure());
		}
		return false;
	}

}
