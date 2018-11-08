/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2003, 2013 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: MethodSpec.java 23401 2010-02-02 23:56:05Z stephan $
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.ast;

import java.util.Arrays;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.CompilationResult.CheckPoint;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.ExpressionContext;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.Expression.DecapsulationState;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions.WeavingScheme;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.lookup.InferenceContext18;
import org.eclipse.jdt.internal.compiler.lookup.InvocationSite;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedGenericMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReasons;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TagBits;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;
import org.eclipse.objectteams.otdt.core.compiler.IOTConstants;
import org.eclipse.objectteams.otdt.core.exceptions.InternalCompilerError;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.AnchorMapping;
import org.eclipse.objectteams.otdt.internal.core.compiler.lookup.CallinCalloutScope;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.MethodModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.RoleModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.TeamModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer.MethodSignatureEnhancer;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.RoleTypeCreator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.TSuperHelper;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.TypeAnalyzer;

/**
 * NEW for OTDT.
 *
 * Specifier for a method in a method binding.
 * Supports resolving in several steps.
 *
 * @author Markus Witte
 * @version $Id: MethodSpec.java 23401 2010-02-02 23:56:05Z stephan $
 */
public class MethodSpec extends ASTNode implements InvocationSite
{
	// ==== structural fields (AST): ====
	public char[] selector;
	public TypeReference returnType;
	public boolean hasSignature;
	public boolean covariantReturn;
	public Argument[] arguments;
	public TypeParameter[] typeParameters;

	// includes everything
	public int declarationSourceStart;
	public int declarationSourceEnd;
	// sourceStart and sourceEnd only include the method name

	// ==== resolved information: ====
    public TypeBinding[] parameters;
    public MethodBinding resolvedMethod;
    public boolean[] argNeedsTranslation; // lifting or lowering: one flag for each argument
	public boolean returnNeedsTranslation;

	public boolean isDeclaration = false;
//{OTDyn:
	public int callinID = -1;
	public int accessId;
// SH}

	public enum ImplementationStrategy {
		/** Callout is translated to direct access to the base feature. */
		DIRECT,
		/** Callout uses a decapsulation wrapper generated by the (old) OTRE. */
		DECAPS_WRAPPER,
		/** Callout uses a generic _OT$access or _OT$accessStatic method generated by the OTREDyn. */
		DYN_ACCESS
	}
	public ImplementationStrategy implementationStrategy = ImplementationStrategy.DIRECT;

	/**
	 * Parsing thought it was reading a method declaration.
	 * Convert this into a MethodSpec now.
	 *
	 * @param md
	 */
    public MethodSpec(AbstractMethodDeclaration md) {
		super();
        this.hasSignature = true;
		this.selector = md.selector;

		this.sourceStart = md.sourceStart;
		this.sourceEnd   = md.sourceStart + md.selector.length - 1;
        this.declarationSourceStart = md.declarationSourceStart; // starts with returnType
		this.declarationSourceEnd   = md.sourceEnd;

		if (md instanceof MethodDeclaration) {
			this.typeParameters = ((MethodDeclaration)md).typeParameters;
			this.returnType = ((MethodDeclaration)md).returnType;
		} else {
			// missing return type, insert 'void' to help downstream not to NPE
			this.returnType = new SingleTypeReference(TypeConstants.VOID, (((long)this.sourceStart)<<32) + this.sourceStart);
			this.returnType.sourceStart = md.sourceStart;
			this.returnType.sourceEnd = md.sourceStart;
		}
		this.arguments = md.arguments;
        if (this.arguments != null) {
        	this.argNeedsTranslation = new boolean[this.arguments.length];
        	Arrays.fill(this.argNeedsTranslation, false);
        }
	}

    /**
     * Constructor for 'short' methodspecs and for use by {Source,Binary}TypeConverter
     */
	public MethodSpec(char[] ident, long pos) {
		super();
		this.hasSignature = false;
		this.selector = ident;

		this.sourceStart = (int) (pos >>> 32);
		this.sourceEnd = (int) (pos & 0xFFFFFFFF);
		this.declarationSourceStart = this.sourceStart;
		this.declarationSourceEnd   = this.sourceEnd;
	}

	/**
	 * Constructor for 'short' methodspecs
	 */
	public MethodSpec(char[] ident, int sStart, int sEnd) {
		super();
		this.hasSignature = false;
		this.selector = ident;

		this.sourceStart = sStart;
		this.sourceEnd   = sEnd;
		this.declarationSourceStart = this.sourceStart;
		this.declarationSourceEnd   = this.sourceEnd;
	}


	public StringBuffer printReturnType(int indent, StringBuffer output) {
		if (this.returnType != null)
		{
			this.returnType.print(indent, output);
			if (this.covariantReturn)
				output.append('+');
			output.append(' ');
		}
		return output;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.compiler.ast.ASTNode#print(int, java.lang.StringBuffer)
	 */
	@Override
	public StringBuffer print(int indent, StringBuffer output) {
		printIndent(indent,output);
		if (this.hasSignature) {
			printReturnType(0,output);

			output.append(new String(this.selector) + "("); //$NON-NLS-1$

			if (this.arguments != null) {
				for (int i = 0; i < this.arguments.length; i++) {
					this.arguments[i].print(indent,output);
					if (i != (this.arguments.length - 1))
						output.append(", "); //$NON-NLS-1$
				}
			}
			output.append(")"); //$NON-NLS-1$
		} else {
			output.append(new String(this.selector));
		}
		return output;
	}

    /**
     * Resolve and bind arguments, return type.
     * @param scope used for resolving. Newly bound arguments are entered here.
     * @param isBaseSide TODO
     */
   public void resolveTypes(CallinCalloutScope scope, boolean isBaseSide) {
		if (this.typeParameters != null) {
			for (int i = 0, length = this.typeParameters.length; i < length; i++) {
				if (isBaseSide)
					scope.problemReporter().illegalMappingRHSTypeParameter(this.typeParameters[i]);
				else
					this.typeParameters[i].resolve(scope);
			}
			if (!isBaseSide)
				scope.connectTypeVariables(this.typeParameters, true);
		}
	   TypeBinding[] types = Binding.NO_PARAMETERS;
        if (this.arguments != null) {
            types = new TypeBinding[this.arguments.length];
            for (int i=0; i<this.arguments.length; i++) {
                TypeReference type = this.arguments[i].type;
                if (isBaseSide)
                	type.setBaseclassDecapsulation(DecapsulationState.ALLOWED);
				types[i] = type.resolveType(scope);
                if (types[i] != null) {
                	type.resolvedType =
                	         types[i] = RoleTypeCreator.maybeWrapUnqualifiedRoleType(scope, types[i], this.arguments[i]);
                } else {
                	// ensure we have a type set!
                	types[i] = type.resolvedType; // a ProblemBinding !?
                	if (types[i] == null)
                		types[i] = new ProblemReferenceBinding(type.getTypeName(), null, ProblemReasons.NotFound);
                }

                // record in scope, needed for role types anchored to an argument
                // (all arguments must be bound in order!)
                this.arguments[i].bind(scope, types[i], false);
            }
        }
        if (this.hasSignature)
        	this.argNeedsTranslation = new boolean[types.length];
        if (this.returnType != null) {
            if (isBaseSide)
            	this.returnType.setBaseclassDecapsulation(DecapsulationState.ALLOWED);
            this.returnType.resolve(scope);
            if (this.returnType.resolvedType != null)
            	this.returnType.resolvedType = RoleTypeCreator.maybeWrapUnqualifiedRoleType(
            									scope, this.returnType.resolvedType, this.returnType);
        }
        this.parameters = types;
   	}

	/**
	 * Hook method (overridden by SelectionOnMethodSpec).
	 * Is called after MethodMappingResolver has finished resolving of methodmappings
	 */
	public void resolveFinished()
	{ /* just a hook */
	}

	/**
	 * Resolve the method or field (see FieldAccessSpec).
	 *
	 * @param receiverType receiver of the method call.
	 * @param scope
	 * @param callinExpected whether this method spec is the LHS of a replace callin.
	 * @param isBaseSide     whether this method spec is the RHS (any binding kind)
	 * @param allowEnclosing whether a method may be found in an enclosing type of receiverType
	 * @return the resolved method (may be problem method) or null
	 */
    public MethodBinding resolveFeature (ReferenceBinding receiverType, BlockScope scope, boolean callinExpected, boolean isBaseSide, boolean allowEnclosing) {
    	// getRealClass() is used, because decapsulation needs to find private methods,
    	// which for roles are found only in the class part.
   		ReferenceBinding receiverClass = receiverType.getRealClass();
		boolean isConstructorSpec = CharOperation.equals(this.selector, receiverClass.sourceName());
		char[] realSelector = isConstructorSpec ? TypeConstants.INIT : this.selector;
		if (this.hasSignature) {
   	    	TypeBinding[] enhancedParameters = this.parameters;
   	    	// first chance: try enhanced:
//			enhancedParameters = MethodSignatureEnhancer.enhanceParameters(scope, this.parameters);
   	    	CompilationResult compilationResult = scope.referenceContext().compilationResult();
			CheckPoint cp = compilationResult.getCheckPoint(scope.referenceContext());
			
			this.resolvedMethod = TypeAnalyzer.findMethod(scope, receiverClass, realSelector, enhancedParameters, isBaseSide, isBaseSide ? this : null);
			if (   !this.resolvedMethod.isValidBinding()
				&& this.resolvedMethod.problemId() == ProblemReasons.NotFound)
			{
				// second+ chance: try plain:
				while (receiverClass != null) {
					compilationResult.rollBack(cp);
					MethodBinding plainMethod = TypeAnalyzer.findMethod(scope, receiverClass, realSelector, this.parameters, isBaseSide, isBaseSide ? this : null);
					if (!callinExpected) {
						this.resolvedMethod = plainMethod;
					} else {
						if (plainMethod != null && plainMethod.isValidBinding())
							scope.problemReporter().replaceMappingToNonCallin(this, plainMethod);
						// mark the ProblemMethodBinding consistently to what we have been looking for last:
						this.resolvedMethod.modifiers |= ExtraCompilerModifiers.AccCallin | ClassFileConstants.AccStatic;
					}
					if (plainMethod != null && plainMethod.isValidBinding())
						break;
					if (allowEnclosing)
						receiverClass = receiverClass.enclosingType();
					else
						receiverClass = null;
				}
			}
   		} else {
   	    	CompilationResult compilationResult = scope.referenceContext().compilationResult();
			CheckPoint cp = compilationResult.getCheckPoint(scope.referenceContext());
			while (receiverClass != null) {
				this.resolvedMethod = receiverClass.getMethod(scope, realSelector);
				if (this.resolvedMethod != null && this.resolvedMethod.isValidBinding())
					break; // good
				if (!allowEnclosing)
					break; // bad
				compilationResult.rollBack(cp);
				receiverClass = receiverClass.enclosingType();
			}
   		}
		if (this.resolvedMethod != null) {
			if (this.resolvedMethod.isValidBinding()) {
				// check visibility of role-side in callin:
				if (!isBaseSide 
						&& scope.referenceContext() instanceof CallinMappingDeclaration 
						&& !this.resolvedMethod.canBeSeenBy(this, scope)) 
				{
					scope.problemReporter().invisibleMethod(this, this.resolvedMethod);
					this.resolvedMethod = new ProblemMethodBinding(this.resolvedMethod, this.selector, this.parameters, ProblemReasons.NotVisible);
				}				
			}
			if (!this.resolvedMethod.isValidBinding() && this.resolvedMethod.declaringClass == null)
				this.resolvedMethod.declaringClass = receiverClass; // needed for computeUniqueKey (via CallinCalloutBinding.computeUniqueKey)
		}
		return this.resolvedMethod;
	}

    /**
     * Same as above, but consider specified signature for argument mapping (using AnchorMapping).
     * @param receiverType receiver of the method call.
     * @param scope
     * @param isBaseSide
     * @param callinExpected whether this method spec is the LHS of a replace callin.
     * @param allowEnclosing whether a method may be found in an enclosing type of receiverType
     */
    public void resolveFeatureWithArgMapping(
    		ReferenceBinding receiverType, BlockScope scope, boolean isBaseSide, boolean callinExpected, boolean allowEnclosing)
    {
    	FieldReference receiver = null;
    	if (isBaseSide) {
			// construct temporary faked receiver
    		ReferenceContext referenceContext = scope.referenceContext();
			CheckPoint cp = referenceContext.compilationResult().getCheckPoint(referenceContext);
			receiver = new FieldReference(IOTConstants._OT_BASE, 0L); // can't fail, help the compiler recognize that receiver won't be null below.
    		try {
    			receiver.receiver = ThisReference.implicitThis();
    			receiver.resolveType(scope);
    		} finally {
    			if (receiver.binding == null || !receiver.binding.isValidBinding()) {
    				// resolve didn't work, which happens if role is ifc, thus has no base field
    				// at least set the receiver type.
    				// TODO(SH): this means, role ifcs can not use base-anchored types.
    				receiver.resolvedType = receiverType;
    				referenceContext.compilationResult().rollBack(cp);
    			}
    		}
    	}
		AnchorMapping anchorMapping = null;
		try {
			anchorMapping = AnchorMapping.setupNewMapping(receiver, this.arguments, scope);
			resolveFeature(receiverType, scope, callinExpected, isBaseSide, allowEnclosing);
		} finally {
			AnchorMapping.removeCurrentMapping(anchorMapping);
		}
    }

    /** Answer parameters of the resolved method (source visible ones only). */
    public TypeBinding[] resolvedParameters() {
    	// parameters are first choice, because these might be instantiated parameters
    	if ((this.parameters == null || this.parameters == Binding.NO_PARAMETERS) && this.resolvedMethod != null)
    		this.parameters = this.resolvedMethod.getSourceParameters();

    	return this.parameters;
    }

    public TypeBinding resolvedType() {
    	if (   this.returnType != null) {
    		TypeBinding declaredType= this.returnType.resolvedType;
    		if (declaredType != null && declaredType.isValidBinding())
    			return declaredType;
    	}
    	return boundMethodReturnType();
    }

	private TypeBinding boundMethodReturnType() {
		if (this.resolvedMethod == null)
			return new ProblemReferenceBinding("<missing>".toCharArray(), ProblemReasons.NotFound, null); //$NON-NLS-1$
		if (this.resolvedMethod.isCallin())
    		return MethodModel.getReturnType(this.resolvedMethod);
    	return this.resolvedMethod.returnType;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.compiler.ast.AstNode#traverse(org.eclipse.jdt.internal.compiler.ASTVisitor, org.eclipse.jdt.internal.compiler.lookup.BlockScope)
	 */
	@Override
	public void traverse(
		ASTVisitor visitor,
		BlockScope scope) {
		if(visitor.visit(this, scope))
		{
			if (this.returnType != null)
				this.returnType.traverse(visitor, scope);
			if (this.arguments != null) {
				int argumentLength = this.arguments.length;
				for (int i = 0; i < argumentLength; i++)
					this.arguments[i].traverse(visitor, scope);
			}
		}
		visitor.endVisit(this, scope);
	}

	/**
	 * Prepare an array of bits which signals for each argument
	 * whether translation (lifting/lowering) is required.
	 */
	public void initTranslationBits() {
		int numArgs = 0;
		if (this.resolvedMethod.parameters != null)
			numArgs = this.resolvedMethod.parameters.length;
		this.argNeedsTranslation = new boolean[numArgs];
		Arrays.fill(this.argNeedsTranslation, false);
	}

	public boolean argNeedsTranslation(int idx) {
		if (this.argNeedsTranslation == null)
			return false;
		if (idx < 0 || idx >= this.argNeedsTranslation.length)
			return false;
		return this.argNeedsTranslation[idx];
	}

	/**
	 * After resolveFeature check whether resolve was successful.
	 *
	 * @param type
	 * @param scope
	 */
	public void checkResolutionSuccess(ReferenceBinding type, CallinCalloutScope scope)
	{
		boolean isCallout = ((AbstractMethodMappingDeclaration)scope.referenceContext).isCallout();
		if (this.resolvedMethod == null) {
			scope.problemReporter().unresolvedMethodSpec(this, type, isCallout);
			this.resolvedMethod = new ProblemMethodBinding(this.selector, Binding.NO_PARAMETERS, type, ProblemReasons.NotFound);
			this.resolvedMethod.returnType = scope.getJavaLangObject(); // prevent NPE on missing return type
			return;
		} else if (!this.resolvedMethod.isValidBinding()) {
			switch (this.resolvedMethod.problemId()) {
				case ProblemReasons.Ambiguous:
					scope.problemReporter().ambiguousMethodMapping(this, type, isCallout);
					return;
				case ProblemReasons.NotVisible:
					ReferenceBinding declaringClass = this.resolvedMethod.declaringClass;
					if (!declaringClass.isRole() && ((ProblemMethodBinding)this.resolvedMethod).closestMatch.isProtected()) {
						this.resolvedMethod = ((ProblemMethodBinding)this.resolvedMethod).closestMatch;
					}
					break; // ignore (may already be reported)
				default:
					scope.problemReporter().missingImplementation(this, "Unexpected compile error at MethodSpec "+this); //$NON-NLS-1$
					return;
			}			
		}
		initTranslationBits();
	}


	/**
	 * Check whether this method spec matches the expected staticness.
	 * @param mappingDeclaration enclosing callin mapping
	 * @param shouldBeStatic expected staticness
	 */
	public void checkStaticness(CallinMappingDeclaration mappingDeclaration, boolean shouldBeStatic)
	{
		if (this.resolvedMethod.isStatic() != shouldBeStatic) {
			if (mappingDeclaration.isReplaceCallin() && !this.resolvedMethod.isStatic())
				mappingDeclaration.scope.problemReporter().replaceCallinIncompatibleStatic(mappingDeclaration, this);
			else
				mappingDeclaration.scope.problemReporter().callinIncompatibleStatic(mappingDeclaration, this);
		}
	}

	void checkDecapsulation(ReferenceBinding baseClass, Scope scope) {
		if (!this.resolvedMethod.canBeSeenBy(baseClass, this, scope)) {
			WeavingScheme weavingScheme = scope.compilerOptions().weavingScheme;
			this.implementationStrategy = weavingScheme == WeavingScheme.OTDRE 
					? ImplementationStrategy.DYN_ACCESS : ImplementationStrategy.DECAPS_WRAPPER;
    		this.accessId = createAccessAttribute(scope.enclosingSourceType().roleModel);
   			scope.problemReporter().decapsulation(this, baseClass, scope);
		} else {
			this.implementationStrategy = ImplementationStrategy.DIRECT;
		}
	}

	/**
	 * If 'hasSignature' check the return type of the bound method against the
	 * declared return type.
	 */
	public boolean checkRoleReturnType(CallinCalloutScope scope, boolean isCallout)
	{
		TypeBinding methodReturn = boundMethodReturnType();
		TypeBinding resolvedReturnType = this.returnType.resolvedType;
		TypeBinding firstBound = null;
		if (resolvedReturnType.isTypeVariable()) {
			firstBound = ((TypeVariableBinding)resolvedReturnType).firstBound;

			// if declared return type is a type variable, so must the return type of the resolved method:
			if (!isMethodReturnTypeVariable(this.resolvedMethod)) {
				scope.problemReporter().differentReturnInMethodSpec(this, /*isCallout*/false);
				return false;
			}
		}
		// the role side of a callout may indeed refine the return type
		// of its inherited role method:
		if (isCallout)
			if (this.returnType.resolvedType.isCompatibleWith(methodReturn))
				return true;
		// in other cases types have to be identical:
		if (   !MethodModel.hasUnboundedReturnType(this.resolvedMethod) // unbounded type variable always matches
			&& !TypeAnalyzer.isSameType(scope.enclosingSourceType(), resolvedReturnType, methodReturn)
			&& !TypeAnalyzer.isSameType(scope.enclosingSourceType(), firstBound, methodReturn))
		{
			scope.problemReporter().differentReturnInMethodSpec(
					this,
					((AbstractMethodMappingDeclaration)scope.referenceContext).isCallout());
			return false;
		}
		return true;
	}
	// does the given method have a type variable as its return type?
	private boolean isMethodReturnTypeVariable(MethodBinding method) {
		if (method instanceof ParameterizedGenericMethodBinding) {
			ParameterizedGenericMethodBinding pMethod;
			pMethod= (ParameterizedGenericMethodBinding)method;
			return pMethod.original().returnType.isTypeVariable();
		} else if (method instanceof ProblemMethodBinding) {
			return method.returnType.isTypeVariable();
		}
		return false;
	}
	/**
	 * If 'hasSignature' check the return type of the bound method against the
	 * declared return type.
	 */
	public boolean checkBaseReturnType(CallinCalloutScope scope, int bindDir)
	{
		TypeBinding methodReturn = boundMethodReturnType();
		if (!TypeAnalyzer.isSameType(scope.enclosingSourceType(), this.returnType.resolvedType, methodReturn))
		{
			if (RoleTypeCreator.isCompatibleViaBaseAnchor(scope, methodReturn, this.returnType.resolvedType, bindDir))
				return true;

			if ((methodReturn.tagBits & TagBits.HasMissingType) == 0)
				scope.problemReporter().differentReturnInMethodSpec(
						this,
						((AbstractMethodMappingDeclaration)scope.referenceContext).isCallout());
			return false;
		}
		return true;
	}

	/**
	 * If 'hasSignature' check the parameter types of the bound method against the
	 * declared parameter types.
	 */
	public boolean checkParameterTypes(CallinCalloutScope scope, boolean isBase)
	{
		// retrieve (un-enhanced) parameters from the actual resolved method:
		TypeBinding[] realParameters = this.resolvedMethod.getSourceParameters();
		for (int i = 0; i < realParameters.length; i++) {
			TypeReference specifiedArgType = this.arguments[i].type;
			TypeBinding realParameter = realParameters[i];
			if (!realParameter.isValidBinding() || specifiedArgType.resolvedType == null)
				continue;
			ReferenceBinding baseclass = scope.enclosingReceiverType().baseclass();
			if (isBase && baseclass != null && baseclass.isTeam() && realParameter.isRole())
				realParameter = TeamModel.strengthenRoleType(baseclass, realParameter);
			if (!TypeAnalyzer.isSameType(
					scope.enclosingSourceType(),
					specifiedArgType.resolvedType,
					realParameter)) 
			{
				scope.problemReporter().differentParamInMethodSpec(
						this, specifiedArgType, realParameter,
						((AbstractMethodMappingDeclaration)scope.referenceContext).isCallout());
				return false;
			}
		}
		return true;
	}

	/** delegate to the resolved feature. */
	public boolean isValid() {
		return this.resolvedMethod != null && this.resolvedMethod.isValidBinding();
	}

	/** delegate to the resolved feature. */
	public int problemId() {
		if (this.resolvedMethod == null)
			return ProblemReasons.NotFound;
		return this.resolvedMethod.problemId();
	}

    public char[] readableName () {
    	if (this.resolvedMethod != null)
    		return this.resolvedMethod.readableName();
    	else
    		return this.selector;
    }

	/**
	 * Callout creation might have turned a method into a tsuper version.
	 * Update resolvedMethod to the overriding callout method, if we are affected.
	 */
	public void updateTSuperMethod(ReferenceBinding type) {
		if (this.resolvedMethod == null)
			return;
		if (TSuperHelper.isTSuper(this.resolvedMethod))
		{
			MethodBinding[] methods = type.methods();
			if (methods != null) {
				for (int i = 0; i < methods.length; i++) {
					if (methods[i].overridesTSuper(this.resolvedMethod)) {
						this.resolvedMethod = methods[i];
						return;
					}
				}
			}
			throw new InternalCompilerError("tsuper method not overridden by any method"); //$NON-NLS-1$
		}
	}

	public char[] codegenSeletor() {
		if (this.resolvedMethod != null && this.resolvedMethod.isConstructor())
			return TypeConstants.INIT;
		else
			return this.selector;
	}
	/** Like MethodBinding.signature() but use getSourceParamters() instead of enhanced parameters. */
	public char[] signature(WeavingScheme weavingScheme) {
		return signature(this.resolvedMethod, weavingScheme);
	}
	/**
	 * This method is used by 
	 * {@link org.eclipse.objectteams.otdt.internal.core.compiler.bytecode.CallinMethodMappingsAttribute CallinMethodMappingsAttribute}
	 * for decoding a mapping from its bytecode attribute.
	 * @param method
	 * @return the bytecode level signature of the given method (yet retrenched)
	 */
	public static char[] signature(MethodBinding method, WeavingScheme weavingScheme) {

		StringBuffer buffer = new StringBuffer();
		buffer.append('(');
		// synthetic args for static role method?
		MethodBinding orig = method.copyInheritanceSrc != null ? method.copyInheritanceSrc : method; // normalize to copyInhSrc so reading a callin-attr. from bytes can more easily find the method
		if (weavingScheme == WeavingScheme.OTDRE && orig.declaringClass.isRole() && orig.isStatic()) {
			buffer.append('I');
			buffer.append(String.valueOf(orig.declaringClass.enclosingType().signature()));
		}
		// manual retrenching?
		boolean shouldRetrench = weavingScheme == WeavingScheme.OTRE && method.isCallin();
		int offset = shouldRetrench ? MethodSignatureEnhancer.getEnhancingArgLen(weavingScheme) : 0;
		int paramLen = method.parameters.length;	
		for (int i = offset; i < paramLen; i++) {
			// 'weaken' to that erasure that was used in the tsuper version:
			TypeBinding targetParameter = method.getCodeGenType(i);
			buffer.append(targetParameter.signature());
		}
		buffer.append(')');
		TypeBinding sourceReturnType = shouldRetrench
				? MethodModel.getReturnType(method)
				: method.returnType;
		// 'weaken' to that erasure that was used in the tsuper version:
		MethodBinding tsuperOriginal = (method.otBits & IOTConstants.IsCopyOfParameterized) != 0 
				? method.copyInheritanceSrc.original() 
				: null;
		TypeBinding returnType = (tsuperOriginal != null && tsuperOriginal.returnType.isTypeVariable() && !sourceReturnType.isTypeVariable())
			 ? tsuperOriginal.returnType
			 : sourceReturnType;
		if (returnType.isTypeVariable() && method instanceof ParameterizedGenericMethodBinding)
			returnType = ((ParameterizedGenericMethodBinding) method).reverseSubstitute((TypeVariableBinding)returnType);
		buffer.append(returnType.erasure().signature());
		int nameLength = buffer.length();
		char[] signature = new char[nameLength];
		buffer.getChars(0, nameLength, signature, 0);

		return signature;
	}

	public boolean isPrivate() {
		return this.resolvedMethod != null && this.resolvedMethod.isPrivate();
	}

	public ReferenceBinding getDeclaringClass() {
		if (this.resolvedMethod != null)
			return this.resolvedMethod.declaringClass;
		return null;
	}

	public boolean isCallin() {
		return this.resolvedMethod != null && this.resolvedMethod.isCallin();
	}

	public boolean isStatic() {
		return this.resolvedMethod != null && this.resolvedMethod.isStatic();
	}

	public boolean isFinal() {
		return this.resolvedMethod != null && this.resolvedMethod.isFinal();
	}

	public int getTranslationFlags() {
		int translations = 0;
		if (this.argNeedsTranslation != null)
			for (int i = 0; i < this.argNeedsTranslation.length; i++)
				if (this.argNeedsTranslation[i])
					translations |= (2<<i);

		if (this.returnNeedsTranslation)
			translations |= 1;
		return translations;
	}
//{OTDYN:
	public int getCallinId(TeamModel theTeam) {
		if (this.callinID == -1)
			this.callinID = theTeam.getNewCallinId(this);
		return this.callinID;
	}
// SH}

	public boolean canBeeSeenBy(ReferenceBinding receiverType, Scope scope) {
		if (this.resolvedMethod == null)
			return false;
		return this.resolvedMethod.canBeSeenBy(receiverType, this, scope);
	}


	// implement InvocationSite
	@Override
	public TypeBinding[] genericTypeArguments() {
		return null;
	}
	@Override
	public boolean isSuperAccess() {
		return true; // grant access to inherited methods
	}
	
	@Override
	public boolean isTypeAccess() {
		return this.resolvedMethod != null && this.resolvedMethod.isStatic();
	}

	@Override
	public void setActualReceiverType(ReferenceBinding receiverType) {
		// ignored		
	}

	@Override
	public void setDepth(int depth) {
		// ignored
	}

	@Override
	public void setFieldIndex(int depth) {
		// ignored		
	}

	/**
	 * Used for generic method resolving.
	 */
	@Override
	public TypeBinding invocationTargetType() {
		return null;
	}
	
	@Override
	public InferenceContext18 freshInferenceContext(Scope scope) {
		int nArgs = this.arguments.length;
		final Expression[] expressions = new Expression[nArgs];
		long pos = 0L; // should never be used for error reporting, right?
		for (int i = 0; i < nArgs; i++) {
			Argument argument = this.arguments[i];
			expressions[i] = new SingleNameReference(argument.name, pos);
			expressions[i].resolvedType = argument.type.resolvedType;
		}

		return new InferenceContext18(scope, expressions, this, null);
	}
	
	@Override
	public ExpressionContext getExpressionContext() {
		return ExpressionContext.ASSIGNMENT_CONTEXT;
	}
	
	public int createAccessAttribute(RoleModel roleModel) {
		return roleModel.addInaccessibleBaseMethod(this.resolvedMethod);		
	}
}
