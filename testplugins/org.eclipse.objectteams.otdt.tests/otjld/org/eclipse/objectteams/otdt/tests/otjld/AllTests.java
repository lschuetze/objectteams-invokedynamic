/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 * 
 * Copyright 2010 Stephan Herrmann
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: AllTests.java 23529 2010-02-18 23:06:04Z stephan $
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors:
 * 	  Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.tests.otjld;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.tests.junit.extension.TestCase;
import org.eclipse.jdt.core.tests.util.AbstractCompilerTest;
import org.eclipse.objectteams.otdt.tests.otjld.api.Reflection;
import org.eclipse.objectteams.otdt.tests.otjld.callinbinding.BaseCalls;
import org.eclipse.objectteams.otdt.tests.otjld.callinbinding.CallinMethodBinding;
import org.eclipse.objectteams.otdt.tests.otjld.callinbinding.CallinParameterMapping_LiftingAndLowering;
import org.eclipse.objectteams.otdt.tests.otjld.callinbinding.CallinWithTranslation;
import org.eclipse.objectteams.otdt.tests.otjld.calloutbinding.CalloutMethodBinding;
import org.eclipse.objectteams.otdt.tests.otjld.calloutbinding.CalloutParameterBinding_LiftingAndLowering;
import org.eclipse.objectteams.otdt.tests.otjld.calloutbinding.CalloutToField;
import org.eclipse.objectteams.otdt.tests.otjld.calloutbinding.OverridingAccessRestrictions;
import org.eclipse.objectteams.otdt.tests.otjld.liftlower.AllSmartLiftingTests;
import org.eclipse.objectteams.otdt.tests.otjld.liftlower.DeclaredLifting;
import org.eclipse.objectteams.otdt.tests.otjld.other.AccessModifiers;
import org.eclipse.objectteams.otdt.tests.otjld.other.Exceptions;
import org.eclipse.objectteams.otdt.tests.otjld.other.Java5;
import org.eclipse.objectteams.otdt.tests.otjld.other.Java7;
import org.eclipse.objectteams.otdt.tests.otjld.other.Java8;
import org.eclipse.objectteams.otdt.tests.otjld.other.Misc;
import org.eclipse.objectteams.otdt.tests.otjld.other.Modifiers;
import org.eclipse.objectteams.otdt.tests.otjld.other.OTNullTypeAnnotationTest;
import org.eclipse.objectteams.otdt.tests.otjld.regression.CompilationOrder;
import org.eclipse.objectteams.otdt.tests.otjld.regression.ComplexStructures;
import org.eclipse.objectteams.otdt.tests.otjld.regression.DevelopmentExamples;
import org.eclipse.objectteams.otdt.tests.otjld.regression.ReportedBugs;
import org.eclipse.objectteams.otdt.tests.otjld.roleplaying.AllBindingAmbiguitiesTests;
import org.eclipse.objectteams.otdt.tests.otjld.roleplaying.BaseClassVisibility;
import org.eclipse.objectteams.otdt.tests.otjld.roleplaying.ExplicitRoleCreation;
import org.eclipse.objectteams.otdt.tests.otjld.roleplaying.GC;
import org.eclipse.objectteams.otdt.tests.otjld.roleplaying.LiftingAndLowering;
import org.eclipse.objectteams.otdt.tests.otjld.roleplaying.PlayedByRelation;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.AcquisitionAndInheritanceOfRoleClasses;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.Confinement;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.Covariance;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.ExternalizedRoles;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.FileStructure;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.ImplicitInheritance;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.InheritanceHierarchyOfTeams;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.OldExternalizedRoles;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.RegularRoleInheritance;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.RelevantRole;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.RoleObjectContainment;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.TeamNesting;
import org.eclipse.objectteams.otdt.tests.otjld.rolesandteams.ValueParameters;
import org.eclipse.objectteams.otdt.tests.otjld.syntax.Syntax;
import org.eclipse.objectteams.otdt.tests.otjld.teamactivation.ExplicitTeamActivation;
import org.eclipse.objectteams.otdt.tests.otjld.teamactivation.ImplicitTeamActivation;
import org.eclipse.objectteams.otdt.tests.otjld.teamactivation.Predicates;

/**
 * @author stephan
 */
public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("All OTJLD Tests");
		
		// 1. rolesandteams
		/*1.1*/addComplianceSuite(suite, RoleObjectContainment.testClass());
		/*1.1.10-*/addComplianceSuite(suite, TeamNesting.testClass());
		/*1.2*/addComplianceSuite(suite, InheritanceHierarchyOfTeams.testClass());
		/*1.3*/addComplianceSuite(suite, AcquisitionAndInheritanceOfRoleClasses.testClass());
		/*1.4*/addComplianceSuite(suite, RegularRoleInheritance.testClass());
		/*1.5*/addComplianceSuite(suite, FileStructure.testClass());
		/*1.6*/addComplianceSuite(suite, ExternalizedRoles.testClass());
		
		/*6.2*/addComplianceSuite(suite, OldExternalizedRoles.testClass());
		
		/*1.7*/addComplianceSuite(suite, Confinement.testClass());
		/*1.8*/addComplianceSuite(suite, RelevantRole.testClass());
		/*1.9*/addComplianceSuite(suite, ValueParameters.testClass());
		
		/*0.c*/addComplianceSuite(suite, ImplicitInheritance.testClass());
		/*6.3*/addComplianceSuite(suite, Covariance.testClass());
		
		// 2. roleplaying
		/*2.1*/addComplianceSuite(suite, PlayedByRelation.testClass());
		/*2.2*/addComplianceSuite(suite, LiftingAndLowering.testClass());
		/*2.3*/addComplianceSuite(suite, ExplicitRoleCreation.testClass());
		/*2.4*/addComplianceSuite(suite, BaseClassVisibility.testClass());
		/*2.5*/addComplianceSuite(suite, GC.testClass());
		
		// 3. calloutbinding
		/*3.1*/addComplianceSuite(suite, CalloutMethodBinding.testClass()); // includes a few from 7.2.1
		/*3.2*/addComplianceSuite(suite, CalloutParameterBinding_LiftingAndLowering.testClass());
		/*3.3*/addComplianceSuite(suite, CalloutToField.testClass());
		
		/*7.4*/addComplianceSuite(suite, OverridingAccessRestrictions.testClass());
		
		// 4. callinbinding
		/*4.1*/addComplianceSuite(suite, CallinMethodBinding.testClass()); // includes a few from 7.2.[45]
		/*4.3*/addComplianceSuite(suite, CallinParameterMapping_LiftingAndLowering.testClass());
		/*4.4*/addComplianceSuite(suite, CallinWithTranslation.testClass());
		/*4.5*/addComplianceSuite(suite, BaseCalls.testClass());
		
		// 5. teamactivation
		/*5.2*/addComplianceSuite(suite, ExplicitTeamActivation.testClass());
		/*5.3*/addComplianceSuite(suite, ImplicitTeamActivation.testClass());
		/*5.4*/addComplianceSuite(suite, Predicates.testClass());
		
		// 6.[14] liftlower
		/*6.1*/addComplianceSuite(suite, DeclaredLifting.testClass());
		//6.4: see below
		
		// 7. see below

		// 8. syntax
		/*8.1*/addComplianceSuite(suite, Syntax.testClass());
		
		// api:
		/*9.2*/addComplianceSuite(suite, Reflection.testClass());
		
		// other:
		/*0.a*/addComplianceSuite(suite, AccessModifiers.testClass());
		/*7.1*/addComplianceSuite(suite, Modifiers.testClass());
		/*7.5*/addComplianceSuite(suite, Exceptions.testClass());
		/*A.1*/addComplianceSuite(suite, Java5.testClass());
		/*A.2*/addComplianceSuite(suite, Java7.testClass(), AbstractCompilerTest.F_1_7);
		/*---*/addComplianceSuite(suite, Java8.testClass(), AbstractCompilerTest.F_1_8);
		/*---*/addComplianceSuite(suite, OTNullTypeAnnotationTest.testClass(), AbstractCompilerTest.F_1_8);
		/*0.m*/addComplianceSuite(suite, Misc.testClass());
		
		// regression:
		/*B.1*/addComplianceSuite(suite, ReportedBugs.testClass());
		/*B.2*/addComplianceSuite(suite, CompilationOrder.testClass());
		/*X.2*/addComplianceSuite(suite, DevelopmentExamples.testClass());
		
		addComplianceSuite(suite, ComplexStructures.testClass());

		/*7.3*/suite.addTest(AllBindingAmbiguitiesTests.suite());
		/*6.4*/suite.addTest(AllSmartLiftingTests.suite());

		return suite;
	}
	public static void addComplianceSuite(TestSuite suite, Class<?> testClass) {
		addComplianceSuite(suite, testClass, AbstractCompilerTest.F_9); //AbstractCompilerTest.F_1_5);
	}
	static void addComplianceSuite(TestSuite suite, Class<?> testClass, int compliance) {
		// Reset forgotten subsets tests
		TestCase.TESTS_PREFIX = null;
		TestCase.TESTS_NAMES = null;
		TestCase.TESTS_NUMBERS= null;
		TestCase.TESTS_RANGE = null;
		TestCase.RUN_ONLY_ID = null;
		
		suite.addTest(AbstractCompilerTest.buildMinimalComplianceTestSuite(testClass, compliance));
	}
}
