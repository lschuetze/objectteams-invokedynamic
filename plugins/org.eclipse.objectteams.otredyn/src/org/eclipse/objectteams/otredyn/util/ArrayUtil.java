/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2012 Oliver Frank and others.
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
package org.eclipse.objectteams.otredyn.util;


public abstract class ArrayUtil {
	
	public static String[] stringArrayPrepend(String[] array,String item) {
		
		if (array == null) {
			return new String[] { item } ;
		}
		
		int arr_len = array.length;
		String [] new_arr = new String[array.length+1];
		System.arraycopy(array,0,new_arr,1,arr_len);
		new_arr[0] = item;
		return new_arr;
	}
	
	public static String[] stringArrayAppend(String[] array,String item) {
		
		if (array == null) {
			return new String[] { item } ;
		}
		
		int arr_len = array.length;
		String [] new_arr = new String[array.length+1];
		System.arraycopy(array,0,new_arr,0,arr_len);
		new_arr[arr_len] = item;
		return new_arr;
	}
}
