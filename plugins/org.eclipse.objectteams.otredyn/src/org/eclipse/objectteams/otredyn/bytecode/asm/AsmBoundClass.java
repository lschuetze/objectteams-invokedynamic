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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.objectteams.otredyn.bytecode.AbstractBoundClass;
import org.eclipse.objectteams.otredyn.bytecode.AbstractTeam;
import org.eclipse.objectteams.otredyn.bytecode.IBytecodeProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

/**
 * This class implements the bytecode parsing for {@link AbstractBoundClass}.
 * It parses the bytecode with ASM.
 * @author Oliver Frank
 */
public abstract class AsmBoundClass extends AbstractTeam {
	
	public static final int ASM_API = Opcodes.ASM6;

	private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

	private IBytecodeProvider bytecodeProvider;
	
	/**
	 * just a temporary cache for the bytecode
	 */
	private byte[] bytecode;
	
	/**
	 * ordered lists of qualified callin labels
	 */
	public List<String[]> precedenceses = new ArrayList<String[]>();

	/**
	 * Set of base classes to which the current class or one of its roles as playedBy bindings.
	 * Qualified class names are '.' separated.
	 */
	public Set<String> boundBaseClasses;

	protected AsmBoundClass(String name, String id, IBytecodeProvider bytecodeProvider, ClassLoader loader) {
		super(name, id, loader);
		this.bytecodeProvider = bytecodeProvider;
	}

	/**
	 * Parses the bytecode of a class and uses the set/add... Methods (e.g. addMethod)
	 * of {@link AbstractBoundClass} to set the information
	 */
	@Override
	public synchronized void parseBytecode() {
		if (parsed) {
			// Already parsed, nothing to do
			return;
		}
		
		bytecode = bytecodeProvider.getBytecode(getId());
		if (bytecode == null) {
			if (this.loader != null) {
				try (InputStream stream = this.loader.getResourceAsStream(this.getInternalName()+".class")) {
					if (stream != null) {
						bytecode = readAllBytes(stream);
						bytecodeProvider.setBytecode(getId(), bytecode);
					}
				} catch (IOException e) {
					// silent (from automatic close()).
				}
			}
		}
		if (bytecode == null) {
			//Class is not loaded yet.
			return;
		}
		
		// Don't parse another time
		parsed = true;
		AsmClassVisitor cv = new AsmClassVisitor(this);
		ClassReader cr = null;
		cr = new ClassReader(bytecode);
		
		cr.accept(cv, Attributes.attributes, 0);
		
		//release the bytecode
		bytecode = null;
	}
	
    public byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int capacity = buf.length;
        int nread = 0;
        int n;
        for (;;) {
            while ((n = is.read(buf, nread, capacity - nread)) > 0)
                nread += n;
            if (n < 0)
                break;
            if (capacity <= MAX_BUFFER_SIZE - capacity) {
                capacity = capacity << 1;
            } else {
                if (capacity == MAX_BUFFER_SIZE)
                    throw new OutOfMemoryError("Requested size too large");
                capacity = MAX_BUFFER_SIZE;
            }
            buf = Arrays.copyOf(buf, capacity);
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

	@Override
	public Collection<String> getBoundBaseClasses() {
		return this.boundBaseClasses;
	}

	/**
	 * Returns the bytecode of this class and cache it temporary.
	 * This method is only needed, if getBytecode of the {@link IBytecodeProvider}
	 * is an expensive operation.
	 * @return
	 */
	protected byte[] allocateAndGetBytecode() {
		if (bytecode == null) {
			bytecode = getBytecode();
		}
		return bytecode;
	}
	
	/**
	 * Get the bytecode directly from the {@link IBytecodeProvider}.
	 * This method can be used, if getBytecode of the {@link IBytecodeProvider}
	 * is not an expensive operation.
	 * @return 
	 */
	@Override
	public byte[] getBytecode() {
		return bytecodeProvider.getBytecode(getId());
	}
	
	/**
	 * Releases the bytecode, if it's cached, an set it in the {@link IBytecodeProvider}
	 */
	protected void releaseBytecode() {
		bytecodeProvider.setBytecode(getId(), bytecode);
		bytecode = null;
	}
	
	/**
	 * Returns the {@link IBytecodeProvider} used for this class
	 * @return
	 */
	protected IBytecodeProvider getBytecodeProvider() {
		return bytecodeProvider;
	}
	
	/**
	 * Sets the bytecode. 
	 * If the bytecode is temporary cached, the cache is used.
	 * Otherwise this method give the bytecode directly to the {@link IBytecodeProvider}
	 * @param bytecode
	 */
	protected void setBytecode(byte[] bytecode) {
		//Is the bytecode temporary cached?
		if (this.bytecode == null) {
			// no, then save the bytecode directly in the bytecode provider
			bytecodeProvider.setBytecode(getId(), bytecode);
		} else {
			// yes, then use the cache
			this.bytecode = bytecode;
		}
	}
	
	public int compare(String callinLabel1, String callinLabel2) {
		for (String[] precedences : this.precedenceses) {
			boolean label1Seen = false, label2Seen = false;
			for (String label : precedences) {
				if (label.equals(callinLabel1)) {
					if (label2Seen)
						return -1; // saw two then one: one has lower priority than two
					label1Seen = true;
				} else if (label.equals(callinLabel2)) {
					if (label1Seen)
						return 1; // saw one then two: one has higher priority than two
					label2Seen = true;
				}
			}
		}
		AbstractBoundClass enclosingClass = getEnclosingClass();
		if (enclosingClass != null) {
			String singleName = getInternalName();
			int pos = singleName.lastIndexOf('$');
			singleName = singleName.substring(pos+1);
			if (singleName.startsWith("__OT__"))
				singleName = singleName.substring("__OT__".length());
			// check for precedence at outer level:
			return enclosingClass.compare(singleName+'.'+callinLabel1, singleName+'.'+callinLabel2);
		}
		return callinLabel1.compareTo(callinLabel2);
	}
}
