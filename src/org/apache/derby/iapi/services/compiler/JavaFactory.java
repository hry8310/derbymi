/*

   Derby - Class org.apache.derby.iapi.services.compiler.JavaFactory

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

package org.apache.derby.iapi.services.compiler;

 

/**
 * JavaFactory provides generators for Java constructs.
 * Once Java constructs have been connected into
 * a complete class definition, the class can be generated
 * from them.
 * The generated class is created as a byte-code array that
 * can then be loaded by a class loader or, in our case,
 * the class utilities wrapper around our special class loader.
 * <p>
 * Each method shows the equivalent Java in the line starting
 * "Java:" in the header comment.  Items in the java code that
 * begin with # refer to parameters used in constructing the
 * object.  So, for example, newReturnStatement takes a parameter
 * named value; its Java code is:
 * <verbatim>
   Java: return #value;
   </verbatim>
 * <p>
 * This represents the fact that newReturnStatement returns a
 * object that represents a return statement that returns the
 * value represented by the parameter named value.
 * <p>
 * REVISIT: when StandardException is moved to BasicServices,
 * all of these want to support it so they can throw
 * real NotImplementedYet exceptions. It is expected that alot
 * of this interface can be not-implemented for engines that
 * do not need this complete treatment of the language.
 * <p>
 * Known Java constructs missing from this interface include:
 * <ul>
 * <li> array initializers
 * <li> ,-lists of statements in for segments
 * <li> accessing a field of the current object or class without
 *	including this or the class name
 * <li> declaring a list of variables against one type
 * <li> conversions/coercions/promotions of types
 * <li> empty statement
 * <li> labeled statement
 * <li> switch statement
 * <li> break, continue statements
 * <li> "super" expression (akin to the "this" expression).
 * <li> operations on multi-dimensional arrays
 * </ul>
 * <p>
 * This interface also does not do real compilation -- there are no
 * checks for things like initialization before use of variables,
 * inclusion of catchs on throws, dead code, etc. Its purpose is to
 * let other parts of the system piece together what they know is valid
 * code and get bytecode out of doing that.
 * <p>
 * Also, implementations will require that the constructs be built
 * appropriately or they may fail to produce a valid class.  For example,
 * newStaticMethodCall must be used to call static methods only,
 * not non-static local instance methods.
 * <p>
 * Implementations may be more, or less strict.  You are best off assuming
 * you have to piece together each java construct and be as explicit as
 * possible.  So, constructors must be created with newConstructor, not
 * newMethodBuilder; constructors must include the explicit call to
 * super(...) or this(...), as their first statement; all methods and
 * constructors must contain a final return statement at the end of
 * their code path(s). Method calls will derive the method to call
 * based on the type of the argument, so you must cast arguments as
 * the system will not search for a close method and coerce arguments
 * appropriately.  This includes coercing them to be some superclass or
 * interface that they already are.
 *
 */
public interface JavaFactory {

	public	final	static	String	JAVA_FACTORY_PROPERTY = "derby.module.JavaCompiler";

 
}
