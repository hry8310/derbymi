/*

   Derby - Class org.apache.derby.impl.services.bytecode.BCMethod

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

import org.apache.derby.iapi.services.compiler.ClassBuilder;
import org.apache.derby.iapi.services.compiler.MethodBuilder; 
import org.apache.derby.iapi.services.compiler.LocalField; 

import org.apache.derby.shared.common.sanity.SanityManager;
 

import java.lang.reflect.Modifier;
import java.util.Vector;
import java.io.IOException;

/**
 * MethodBuilder is used to piece together a method when
 * building a java class definition.
 * <p>
 * When a method is first created, it has:
 * <ul>
 * <li> a return type
 * <li> modifiers
 * <li> a name
 * <li> an empty parameter list
 * <li> an empty throws list
 * <li> an empty statement block
 * </ul>
 * <p>
 * MethodBuilder implementations are required to supply a way for
 * Statements and Expressions to give them code.  Most typically, they may have
 * a stream to which their contents writes the code that is of
 * the type to satisfy what the contents represent.
 * MethodBuilder implementations also have to have a way to supply
 * ClassBuilders with their code, that satisfies the type of class
 * builder they are implemented with.  This is implementation-dependent,
 * so ClassBuilders, MethodBuilders, Statements, and Expressions all have
 * to be of the same implementation in order to interact to generate a class.
 * <p>
 * Method Builder implementation for generating bytecode.
 *
 */
class BCMethod implements MethodBuilder {
    
    /**
     * Code length at which to split into sub-methods.
     * Normally set to the maximim code length the
     * JVM can support, but for testing the split code
     * it can be reduced so that the standard tests
     * cause some splitting. Tested with value set to 2000.
     */
    static   int CODE_SPLIT_LENGTH =0;
    
	  BCClass		cb;
	 
	  String myReturnType;
	
	/**
	 * The original name of the method, this
	 * represents how any user would call this method.
	 */
	private   String myName;

    /**
     * Fast access for the parametes, will be null
     * if the method has no parameters.
     */
	BCLocalField[] parameters; 
    
    /**
     * List of parameter types with java language class names.
     * Can be null or zero length for no parameters.
     */
    private   String[] parameterTypes;
    
    
	Vector<String> thrownExceptions; // expected to be names of Classes under Throwable

	CodeChunk myCode;
 

	private int currentVarNum;
	private int statementNum;
	
	/**
	 * True if we are currently switching control
	 * over to a sub method to avoid hitting the code generation
	 * limit of 65535 bytes per method.
	 */
	private boolean handlingOverflow;
	
	/**
	 * How many sub-methods we have overflowed to.
	 */
	private int subMethodCount;

	BCMethod(ClassBuilder cb,
			String returnType,
			String methodName,
			int modifiers,
			String[] parms,
			BCJava factory) {

		 
	}
	//
	// MethodBuilder interface
	//

	/**
	 * Return the logical name of the method. The current
	 * myEntry refers to the sub method we are currently
	 * overflowing to. Those sub-methods are hidden from any caller.
	 */
	public String getName() {
		return myName;
	}

	public void getParameter(int id) {

		int num = parameters[id].cpi;
		short typ = parameters[id].type.vmType();
		if (num < 4)
			myCode.addInstr((short) (CodeChunk.LOAD_VARIABLE_FAST[typ]+num));
		else
			myCode.addInstrWide(CodeChunk.LOAD_VARIABLE[typ], num);

		growStack(parameters[id].type);
	}

	/**
	 * a throwable can be added to the end of
	 * the list of thrownExceptions.
	 */
	public void addThrownException(String exceptionClass) {
		
		// cannot add exceptions after code generation has started.
		// Allowing this would cause the method overflow/split to
		// break as the top-level method would not have the exception
		// added in the sub method.
		if (SanityManager.DEBUG)
		{
			if (myCode.getPC() != 0)
				SanityManager.THROWASSERT("Adding exception after code generation " + exceptionClass
						+ " to method " + getName());
		}

		if (thrownExceptions == null)
			thrownExceptions = new Vector<String>();
		thrownExceptions.add(exceptionClass);
	}

	/**
	 * when the method has had all of its parameters
	 * and thrown exceptions defined, and its statement
 	 * block has been completed, it can be completed and
	 * its class file information generated.
	 * <p>
	 * further alterations of the method will not be
	 * reflected in the code generated for it.
	 */
	public void complete() {
        
        // myCode.getPC() gives the code length since
        // the program counter will be positioned after
        // the last instruction. Note this value can
        // be changed by the splitMethod call.
        
        if (myCode.getPC() > CODE_SPLIT_LENGTH)
            splitMethod();
                         
       // write exceptions attribute info
        writeExceptions();
        	
	 
	}
    
    /**
     * Attempt to split a large method by pushing code out to several
     * sub-methods. Performs a number of steps.
     * <OL>
     * <LI> Split at zero stack depth.
     * <LI> Split at non-zero stack depth (FUTURE)
     * </OL>
     * 
     * If the class has already exceeded some limit in building the
     * class file format structures then don't attempt to split.
     * Most likely the number of constant pool entries has been exceeded
     * and thus the built class file no longer has integrity.
     * The split code relies on being able to read the in-memory
     * version of the class file in order to determine descriptors
     * for methods and fields.
     */
    private void splitMethod() {
        
        int split_pc = 0;
        boolean splittingZeroStack = true;
        for (int codeLength = myCode.getPC();
               (cb.limitMsg == null) &&
               (codeLength > CODE_SPLIT_LENGTH);
            codeLength = myCode.getPC())
        {
            int lengthToCheck = codeLength - split_pc;

            int optimalMinLength;
            if (codeLength < CODE_SPLIT_LENGTH * 2) {
                // minimum required
                optimalMinLength = codeLength - CODE_SPLIT_LENGTH;
            } else {
                // try to split as much as possible
                // need one for the return instruction
                optimalMinLength = CODE_SPLIT_LENGTH - 1;
            }

            if (optimalMinLength > lengthToCheck)
                optimalMinLength = lengthToCheck;
        }
           
        
 
    }

	/*
     * class interface
     */
 

    //
    // Class implementation
    //


	/**
	 * sets exceptionBytes to the attribute_info needed
	 * for a method's Exceptions attribute.
	 * The ClassUtilities take care of the header 6 bytes for us,
	 * so they are not included here.
	 * See The Java Virtual Machine Specification Section 4.7.5,
	 * Exceptions attribute.
	 */
	protected void writeExceptions() {
		if (thrownExceptions == null)
			return;

		int numExc = thrownExceptions.size();

		// don't write an Exceptions attribute if there are no exceptions.
		if (numExc != 0) {

			 		
		}
	}

	/*
	** New push compiler api.
	*/

	/**
	 * Array of the current types of the values on the stack.
	 * A type that types up two words on the stack, e.g. double
	 * will only occupy one element in this array.
	 * This array is dynamically re-sized as needed.
	 */
	private Type[]	stackTypes = new Type[8];
	
	/**
	 * Points to the next array offset in stackTypes
	 * to be used. Really it's the number of valid entries
	 * in stackTypes.
	 */
	private int     stackTypeOffset;

	/**
	 * Maximum stack depth seen in this method, measured in words.
	 * Corresponds to max_stack in the Code attribute of section 4.7.3
	 * of the vm spec.
	 */
	int maxStack;
	
	/**
	 * Current stack depth in this method, measured in words.
	 */
	private int stackDepth;

	private void growStack(int size, Type type) {
		stackDepth += size;
		if (stackDepth > maxStack)
			maxStack = stackDepth;
		
		if (stackTypeOffset >= stackTypes.length) {

			Type[] newStackTypes = new Type[stackTypes.length + 8];
			System.arraycopy(stackTypes, 0, newStackTypes, 0, stackTypes.length);
			stackTypes = newStackTypes;
		}

		stackTypes[stackTypeOffset++] = type;

		if (SanityManager.DEBUG) {

			int sum = 0;
			for (int i = 0 ; i < stackTypeOffset; i++) {
				sum += stackTypes[i].width();
			}
			if (sum != stackDepth) {
				SanityManager.THROWASSERT("invalid stack depth " + stackDepth + " calc " + sum);
			}
		}
	}

	private void growStack(Type type) {
		growStack(type.width(), type);
	}

	private Type popStack() {
		stackTypeOffset--;
		Type topType = stackTypes[stackTypeOffset];
		stackDepth -= topType.width();
		return topType;

	}
	
	private Type[] copyStack()
	{
		Type[] stack = new Type[stackTypeOffset];
		System.arraycopy(stackTypes, 0, stack, 0, stackTypeOffset);
		return stack;
	}

	public void pushThis() {
		 
	}

	public void push(byte value) {
		 
	}

	public void push(boolean value) {
		 
	}

	public void push(short value) {
	 
	}

	public void push(int value) {
	 
	}

	public void dup() {
		Type dup = popStack();
		 
		growStack(dup);
		growStack(dup);

	}

	public void swap() {

		// have A,B
		// want B,A

		Type wB = popStack();
		Type wA = popStack();
		growStack(wB);
		growStack(wA);
 

		// all except the simple swap push an extra
		// copy of B which needs to be popped.
		growStack(wB);
		popStack();

	}

    /**
     * Push an integer value. Uses the special integer opcodes
     * for the constants -1 to 5, BIPUSH for values that fit in
     * a byte and SIPUSH for values that fit in a short. Otherwise
     * uses LDC with a constant pool entry.
     * 
     * @param value Value to be pushed
     * @param type Final type of the value.
     */
	private void push(int value, Type type) {

		CodeChunk chunk = myCode;

		 
		growStack(type.width(), type);
		
	}

    /**
     * Push a long value onto the stack.
     * For the values zero and one the LCONST_0 and
     * LCONST_1 instructions are used.
     * For values betwee Short.MIN_VALUE and Short.MAX_VALUE
     * inclusive an byte/short/int value is pushed
     * using push(int, Type) followed by an I2L instruction.
     * This saves using a constant pool entry for such values.
     * All other values use a constant pool entry. For values
     * in the range of an Integer an integer constant pool
     * entry is created to allow sharing with integer constants
     * and to reduce constant pool slot entries.
     */
	public void push(long value) {
        CodeChunk chunk = myCode;

        
     
    }
	public void push(float value) {

		CodeChunk chunk = myCode;
		
		 
	 
	}

	public void push(double value) {
		CodeChunk chunk = myCode;

		 
		 
	}
	public void push(String value) {
		 
		growStack(1, Type.STRING);
	}
 

	public void methodReturn() {

		short opcode;		
		 
	}

	public Object describeMethod(short opcode, String declaringClass, String methodName, String returnType) {

		Type rt = cb.factory.type(returnType);

		 

		return new BCMethodCaller(opcode, rt, 0);
	}

	public int callMethod(Object methodDescriptor) {
 
		return 0;
	}

	public int callMethod(short opcode, String declaringClass, String methodName,
		String returnType, int numArgs) {

		Type rt = cb.factory.type(returnType);

		int initialStackDepth = stackDepth;

		// get the array of parameter types

		String [] debugParameterTypes = null;
		String[] vmParameterTypes;
		if (numArgs == 0) {
			vmParameterTypes = BCMethodDescriptor.EMPTY;
		} else {
			if (SanityManager.DEBUG) {
				debugParameterTypes = new String[numArgs];
			}
			vmParameterTypes = new String[numArgs];
			for (int i = numArgs - 1; i >= 0; i--) {
				Type at = popStack();

				vmParameterTypes[i] = at.vmName();
				if (SanityManager.DEBUG) {
					debugParameterTypes[i] = at.javaName();
				}
			}
		}
		
		String methodDescriptor = BCMethodDescriptor.get(vmParameterTypes, rt.vmName(), cb.factory);
 
		return 0;
	}

	private Type vmNameDeclaringClass(String declaringClass) {
		if (declaringClass == null)
			return null;
		return cb.factory.type(declaringClass);
	}

	public void callSuper() {

	 
	}

	public void pushNewStart(String className) {

	 
	}

	public void pushNewComplete(int numArgs) {
	 
	}

	public void upCast(String className) {
		Type uct = cb.factory.type(className);

		stackTypes[stackTypeOffset - 1] = uct;
		//popStack();
		//growStack(1, uct);
	}

	public void cast(String className) {
		
		// Perform a simple optimization to not
		// insert a checkcast when the classname
		// of the cast exactly matches the type name
		// currently on the stack.
		// This can reduce the amount of generated code.
		// This compiler/class generator does not load
		// classes to check relationships or any other
		// information. Thus other optimizations where a cast
		// is not required are not implemented.
		Type tbc = stackTypes[stackTypeOffset - 1];
		
		short sourceType = tbc.vmType();
		
		if (sourceType == BCExpr.vm_reference)
		{
			// Simple optimize step
			if (className.equals(tbc.javaName()))
			{
				// do nothing, exact matching type
				return;
			}
		}
		
		Type ct = cb.factory.type(className);
		popStack();
		
		short targetType = ct.vmType();

		if (SanityManager.DEBUG) {

			if (!((sourceType == BCExpr.vm_reference &&
				targetType == BCExpr.vm_reference) ||
				(sourceType != BCExpr.vm_reference &&
				targetType != BCExpr.vm_reference))) {
				SanityManager.THROWASSERT("Both or neither must be object types " + ct.javaName() + " " + tbc.javaName());
			}
		}

		 
		growStack(ct);
	}

	public void isInstanceOf(String className) {
	 
		popStack(); 
	}

	public void pushNull(String type) {
		 
		growStack(1, cb.factory.type(type));
	}


	public void getField(LocalField field) {

		BCLocalField lf = (BCLocalField) field;
		Type lt = lf.type;

		pushThis();
	 

		popStack();
		growStack(lt);

	}

	public void getField(String declaringClass, String fieldName, String fieldType) {
		Type dt = popStack();

		Type dtu = vmNameDeclaringClass(declaringClass);
		if (dtu != null)
			dt = dtu;
 
	}
	/**
		Push the contents of the described static field onto the stack.		
	*/
	public void getStaticField(String declaringClass, String fieldName, String fieldType) {
		 
	}

	private void getField(short opcode, String declaringClass, String fieldName, String fieldType) { 

		Type ft = cb.factory.type(fieldType);
		 
		growStack(ft);
	}
	
	/**
	 * Set the field but don't duplicate its value so
	 * nothing is left on the stack after this call.
	 */
	public void setField(LocalField field) {
		BCLocalField lf = (BCLocalField) field;
		putField(lf.type, lf.cpi, false);
        overflowMethodCheck();
	}

	/**
		Upon entry the top word(s) on the stack is
		the value to be put into the field. Ie.
		we have
		<PRE>
		word
		</PRE>

		Before the call we need 
		<PRE>
		word
		this
		word
		</PRE>
		word2,word1 -> word2, word1, word2

		So that we are left with word after the put.

	*/
	public void putField(LocalField field) {
		BCLocalField lf = (BCLocalField) field;
		putField(lf.type, lf.cpi, true);
	}

	/**
		Pop the top stack value and store it in the instance field of this class.
	*/
	public void putField(String fieldName, String fieldType) {

		Type ft = cb.factory.type(fieldType);
		 

		putField(ft, 0, true);
	}

	private void putField(Type fieldType, int cpi, boolean dup) {

		 
		// now have
		// dup true:  ...,value,value
		// dup false: ...,value,

		pushThis();
		// now have
		// dup true:  ...,value,value,this
		// dup false: ...,value,this

		swap();
		// now have
		// dup true:  ...,value,this,value
		// dup false: ...,this,value

		 
		popStack(); // the value
		popStack(); // this

		// now have
		// dup true:  ...,value
		// dup false: ...
	}
	/**
		Pop the top stack value and store it in the field.
		This call requires the instance to be pushed by the caller.
	*/
	public void putField(String declaringClass, String fieldName, String fieldType) {
		Type vt = popStack();
		Type dt = popStack();

		if (SanityManager.DEBUG) {
			if (dt.width() != 1)
				SanityManager.THROWASSERT("reference expected for field access - is " + dt.javaName());
		}

		// have objectref,value
		// need value,objectref,value

	 
		growStack(vt);
		growStack(dt);
		growStack(vt);

		Type dtu = vmNameDeclaringClass(declaringClass);
		if (dtu != null)
			dt = dtu;

		Type ft = cb.factory.type(fieldType);
		 

		popStack(); // value
		popStack(); // reference
	}

	public void conditionalIfNull() {

		 
	}

	public void conditionalIf() {
		 
	}

	private Conditional condition;

	private void conditionalIf(short opcode) {
		popStack();
		
		// Save the stack upon entry to the 'then' block of the
		// 'if' so that we can set up the 'else' block with the
		// correct stack on entry.

		condition = new Conditional(condition, myCode, opcode, copyStack());
	}

	public void startElseCode() {
		
		// start the else code
		Type[] entryStack = condition.startElse(this, myCode, copyStack());
		
		for (int i = stackDepth = 0; i  < entryStack.length; i++)
		{
			stackDepth += (stackTypes[i] = entryStack[i]).width();
		}
		this.stackTypeOffset = entryStack.length;

	}
	public void completeConditional() {
		condition = condition.end(this, myCode, stackTypes, stackTypeOffset);
	}
	
	public void pop() {
		if (SanityManager.DEBUG) {
			if (stackDepth == 0)
				SanityManager.THROWASSERT("pop when stack is empty!");
		}
		Type toPop = popStack();

		 
		
        overflowMethodCheck();
	}	

	public void endStatement() {
		if (stackDepth != 0) {
			pop();
		}

		//if (SanityManager.DEBUG) {
		//	if (stackDepth != 0)
		//		SanityManager.THROWASSERT("items left on stack " + stackDepth);
	//	}
	}

	/**
	*/
	public void getArrayElement(int element) {

		push(element);
		popStack(); // int just pushed will be popped by array access

		Type arrayType = popStack();

		String arrayJava = arrayType.javaName();
		String componentString = arrayJava.substring(0,arrayJava.length()-2);

		Type componentType = cb.factory.type(componentString);

		short typ = componentType.vmType();

		// boolean has a type id of integer, here it needs to be byte.
		if ((typ == BCExpr.vm_int) && (componentType.vmName().equals("Z")))
			typ = BCExpr.vm_byte;
		myCode.addInstr(CodeChunk.ARRAY_ACCESS[typ]);

		growStack(componentType);

	}
	// come in with ref, value

	public void setArrayElement(int element) {

		// ref, value

		push(element);

		// ref, value, index
		swap();
		
		Type componentType = popStack(); // value
		popStack(); // int just pushed will be popped by array access
		
		popStack(); // array ref.

		short typ = componentType.vmType();

		// boolean has a type id of integer, here it needs to be byte.
		if ((typ == BCExpr.vm_int) && (componentType.vmName().equals("Z")))
			typ = BCExpr.vm_byte;

		myCode.addInstr(CodeChunk.ARRAY_STORE[typ]);
	}
	/**
		this array maps the BCExpr vm_* constants 0..6 to
		the expected VM type constants for the newarray instruction.
		<p>
		Because boolean was mapped to integer for general instructions,
		it will have to be specially matched and mapped to its value
		directly (4).
	 */
	private static final byte newArrayElementTypeMap[] = { 8, 9, 10, 11, 6, 7, 5 };
	static final byte T_BOOLEAN = 4;
	/**
		Create an array instance

		Stack ... =>
		      ...,arrayref
	*/
	public void pushNewArray(String className, int size) {

		push(size);
		popStack(); // int just pushed will be popped by array creation

		Type elementType = cb.factory.type(className);

		// determine the instruction to use based on the element type
		if (elementType.vmType() == BCExpr.vm_reference) {

			// For an array of Java class/interface elements, generate:
			// ANEWARRAY #cpei ; where cpei is a constant pool index for the class

			 
			// Use U2, not CPE, since only wide form exists.
		 
		} else {
			byte atype;

		 
 
		}

		// an array reference is an object, hence width of 1
		growStack(1, cb.factory.type(className.concat("[]")));
	}
    
    /**
     * Write a instruction that uses a constant pool entry
     * as an operand, add a limit exceeded message if
     * the number of constant pool entries has exceeded
     * the limit.
     */
    private void addInstrCPE(short opcode, int cpe)
    {
        
        myCode.addInstrCPE(opcode, cpe);
    }

	/**
		Tell if statement number in this method builder hits limit.  This
		method builder keeps a counter of how many statements are added to it.
		Caller should call this function every time it tries to add a statement
		to this method builder (counter is increased by 1), then the function
		returns whether the accumulated statement number hits a limit.
		The reason of doing this is that Java compiler has a limit of 64K code
		size for each method.  We might hit this limit if an extremely long
		insert statement is issued, for example (see beetle 4293).  Counting
		statement number is an approximation without too much overhead.
	*/
	public boolean statementNumHitLimit(int noStatementsAdded)
	{
		if (statementNum > 2048)    // 2K limit
		{
			return true;
		}
		else
		{
			statementNum = statementNum + noStatementsAdded;
			return false;
		}
	}
	
	/**
	 * Check to see if the current method byte code is nearing the
	 * limit of 65535. If it is start overflowing to a new method.
	 * <P>
	 * Overflow is handled for a method named e23 as:
	 * <CODE>
	 public Object e23()
	 {
	   ... existing code
	   // split point
	   return e23_0();
	 }
	 private Object e23_0()
	 {
	    ... first set overflowed code
	    // split point
	    return e23_1(); 
	 }
	 private Object e23_1()
	 {
	    ... second set overflowed code
	    // method complete
	    return result; 
	 }
	 	 </CODE>
	 <P>
	 
	 These overflow methods are hidden from the code using this MethodBuilder,
	 it continues to think that it is building a single method with the
	 original name.


	 * <BR> Restrictions:
	 * <UL>
	 * <LI> Only handles methods with no arguments
	 * <LI> Stack depth must be zero
	 * </UL>
	 * 
	 *
	 */
	private void overflowMethodCheck()
	{
        if (stackDepth != 0) {
            // Can only overflow to new method if the stack is empty.
            return;
        }

		if (handlingOverflow)
			return;
		
		// don't sub method in the middle of a conditional
		if (condition != null)
			return;
		
		int currentCodeSize = myCode.getPC();
		
		// Overflow at >= 55,000 bytes which is someway
		// below the limit of 65,535. Ideally overflow
		// would occur at 65535 minus the few bytes needed
		// to call the sub-method, but the issue is at this level
		// we don't know frequently we are called given the restriction
		// of only being called when the stack depth is zero.
		// Thus split earlier to try ensure most cases are caught.
		// Only downside is that we may split into N methods when N-1 would suffice.
		if (currentCodeSize < 55000)
			return;
				
		// only handle no-arg methods at the moment.
		if (parameters != null)
		{
			if (parameters.length != 0)
				return;
		}
        		
		BCMethod subMethod = getNewSubMethod(myReturnType, false);
				
		// stop any recursion
		handlingOverflow = true;
		
		// in this method make a call to the sub method we will
		// be transferring control to.
        callSubMethod(subMethod);
	
		// and return its value, works just as well for a void method!
		this.methodReturn();
		this.complete();
		
		handlingOverflow = false;
		
		// now the tricky bit, make this object take over the
		// code etc. from the sub method. This is done so
		// that any code that has a reference to this MethodBuilder
		// will continue to work. They will be writing code into the
		// new sub method.
		 
		this.myCode = subMethod.myCode;
		this.currentVarNum = subMethod.currentVarNum;
		this.statementNum = subMethod.statementNum;
		
		// copy stack info
		this.stackTypes = subMethod.stackTypes;
		this.stackTypeOffset = subMethod.stackTypeOffset;
		this.maxStack = subMethod.maxStack;
		this.stackDepth = subMethod.stackDepth;
	}
	
    /**
     * Create a sub-method from this method to allow the code builder to split a
     * single logical method into multiple methods to avoid the 64k per-method
     * code size limit. The sub method with inherit the thrown exceptions of
     * this method.
     * 
     * @param returnType
     *            Return type of the new method
     * @param withParameters
     *            True to define the method with matching parameters false to
     *            define it with no parameters.
     * @return A valid empty sub method.
     */
    final BCMethod getNewSubMethod(String returnType, boolean withParameters) {
  
        return null;
    }

    /**
     * Call a sub-method created by getNewSubMethod handling parameters
     * correctly.
     */
    final void callSubMethod(BCMethod subMethod) {
        
    }
}

