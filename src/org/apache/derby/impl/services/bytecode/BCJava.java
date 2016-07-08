/*

   Derby - Class org.apache.derby.impl.services.bytecode.BCJava

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.services.bytecode;

import java.util.Properties;

import org.apache.derby.iapi.error.StandardException; 
import org.apache.derby.iapi.services.compiler.ClassBuilder;
import org.apache.derby.iapi.services.compiler.JavaFactory; 
import org.apache.derby.shared.common.sanity.SanityManager;

/**
	<p>
	<b>Debugging problems with generated classes</b>
	<p>
	When the code has been generated incorrectly, all sorts of
	odd things can go wrong.  This is one recommended approach to
	finding the problem.
	<p>
	First, turn on ByteCodeGenInstr and DumpClassFile. Look
	for missing files (right now they are consecutively numbered
	by the activation class builder; later on they won't be, but
	BytCodeGenInstr dumps messages about the classes it has).
	Look at the log to make sure that all "GEN starting class/method"
	messages are paired with a "GEN ending class/method" message.
	If a file is missing or the pairing is missing, then something
	went wrong when the system tried to generate the bytecodes.
	Resort to your favorite debugging tool to step through
	the faulty statement.
	<p>
	If you get class files but the system crashes on you (I had
	an OS segmentation fault once) or you get funny messages like
	JDBC Excpetion: ac5 where ac5 is just the name of a generated
	class, then one of the following is likely:
	<ul>
	<li> you are calling INVOKEVIRTUAL when
	     you are supposed to call INVOKEINTERFACE
	<li> you have an inexact match on a method argument or
	     return type.
	<li> you are trying to get to a superclass's field using
	     a subclass.
	</ul>
	The best way to locate the problem here is to do this (replace
	ac5.class with the name of your class file):
	<ol>
	<li> javap -c -v ac5 >ac5.gp<br>
		 if javap reports "Class not found", and the file ac5.class does
		 exist in the current directory, then the .class file is probably
		 corrupt.  Try running mocha on it to see if that works. The
		 problem will be in the code that generates the entries for
		 the class file -- most likely the ConstantPool is bad, an
		 attribute got created incorrectly, or
		 perhaps the instruction streams are goofed up.
	<li> java mocha.Decompiler ac5.class<br>
	     if mocha cannot create good java source, then you really
	     need to go back and examine the calls creating the java 
	     constructs; a parameter might have been null when it should
	     have, a call to turn an expression into a statement may be
	     missing, or something else may be wrong.
	<li> mv ac5.mocha ac5.java
	<li> vi ac5.java ; you will have to fix any new SQLBoolean(1, ...)
	     calls to be new SQLBoolean(true, ...).  Also mocha 
	     occasionally messes up other stuff too.  Just iterate on it
	     until it builds or you figure out what is wrong with
	     the generated code.
	<li> javac ac5.java
	<li> javap -v -c ac5 >ac5.jp
	<li> sed '1,$s/#[0-9]* </# </' ac5.gp > ac5.gn
	<li> sed '1,$s/#[0-9]* </# </' ac5.jp > ac5.jn<br>
	     These seds are to get rid of constant pool entry numbers,
	     which will be wildly different on the two files.
	<li> vdiff32 ac5.gn ac5.jn<br>
	     this tool shows you side-by-side diffs.  If you change
	     to the window that interleaves the diffs, you can see the 
	     length of the line.  Look for places where there are
	     invokevirtual vs. invokeinterface differences, differences
	     in the class name of a field, differences in the class name
	     of a method parameter or return type.  The generated code
	     *will* have some unavoidable differences from the
	     compiled code, such as:
	     <ul>
	     <li> it will have goto's at the end of try blocks
		  rather than return's.
	     <li> it will do a getstatic on a static final field
		  rather than inlining the static final field's value
	     <li> it will have more checkcast's in it, since it
		  doesn't see if the checkcast will always succeed
		  and thus remove it.
	     </ul>
	     Once you find a diff, you need to track down where
	     the call was generated and modify it appropriately:
	     change newMethodCall to newInterfaceMethodCall;
	     add newCastExpression to get a argument into the right
	     type for the parameter; ensure the return type given for
	     the method is its declared return type.
	</ol>
	@see org.apache.derby.iapi.services.compiler.JavaFactory

 */
public class BCJava implements JavaFactory {

	 
	//
	// class interface
	//
	public BCJava() {
	 
	}

	//
	// ModuleControl interface
	//
	/**
	 * Start this module. We need a read/write version of the class utilities
	 * 
	 * @exception StandardException
	 *                standard Derby policy
	 */
	public void boot(boolean create, Properties properties)
			throws StandardException {
 
	}

	/**
	 * Stop this module. In this case, nothing needs to be done.
	 */
	public void stop() {
	}

	//
	// JavaFactory interface
	//
  

	// /////////////////////////////////////////
	//
	// UTILITIES specific to this implementation
	//
	// //////////////////////////////////////////

	/**
	 * Get the VM Type ID that corresponds with the given java type name. This
	 * uses the cache of VM type ids.
	 *
	 * @param javaType
	 *            The java type name to translate to a java VM type id
	 *
	 * @return The java VM type ID
	 */
	Type type(String javaType) {

		Type retval;
 
		return null;
	}

	String vmType(BCMethodDescriptor md) {
		String retval;

		 

		return null;
	}

	/**
	 * Map vm types as strings to vm types as the VM handles, with int ids. Used
	 * in mapping opcodes based on type of operand/stack entry available.
	 */
	static short vmTypeId(String vmTypeS) {
		char vmTypeC = vmTypeS.charAt(0);
		  
		return BCExpr.vm_void;
	}
}
