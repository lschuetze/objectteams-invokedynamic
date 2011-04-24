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
 * $Id: TeamSmapGenerator.java 23417 2010-02-03 20:13:55Z stephan $
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/

package org.eclipse.objectteams.otdt.internal.core.compiler.smap;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.objectteams.otdt.core.compiler.ISMAPConstants;

/** Generates smap for a TeamType
 * * @author ike
 */
public class TeamSmapGenerator extends AbstractSmapGenerator
{

    /**
     * @param type
     */
    public TeamSmapGenerator(TypeDeclaration type)
    {
        super(type);
    }

    public char[] generate()
    {
        for (Iterator<SmapStratum> iter = this._strata.iterator(); iter.hasNext();)
        {
            SmapStratum stratum = iter.next();

            if(stratum.getStratumName().equals(ISMAPConstants.OTJ_STRATUM_NAME))
            {
                return generateOTJSmap(stratum);
            }
        }

        return null;
    }


    // current reasons why team re-maps source positions:
    // - TeamMethodGenerator copies methods from o.o.Team to application team.
    private char[] generateOTJSmap(SmapStratum stratum)
    {
    	LineInfoCollector lineInfoCollector = new LineInfoCollector();
        LineNumberProvider provider = this._type.getTeamModel().getLineNumberProvider();

        Set<ReferenceBinding> copySources = provider.getLineInfos().keySet();
        if (copySources.isEmpty())
        	return null;

        for (Iterator<ReferenceBinding> copySourcesIter = copySources.iterator(); copySourcesIter.hasNext();)
        {
            ReferenceBinding copySrc = copySourcesIter.next();
            List <LineInfo> lineInfos = provider.getLineInfosForType(copySrc);

            // simple enclosingType() is enough, since we know its not a nested team (currently handled by RoleSmapGenerator)
            FileInfo fileInfo = getOrCreateFileInfoForType(stratum, copySrc.enclosingType());
            fileInfo.addLineInfo(lineInfos);
            lineInfoCollector.storeLineInfos(lineInfos);
        }

        return getSMAP().toCharArray();
    }
}

