/*

   Derby - Class org.apache.derby.impl.services.bytecode.CodeChunk

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

 

import org.apache.derby.shared.common.sanity.SanityManager; 

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * This class represents a chunk of code in a CodeAttribute.
 * Typically, a CodeAttribute represents the code in a method.
 * If there is a try/catch block, each catch block will get its
 * own code chunk.  This allows the catch blocks to all be put at
 * the end of the generated code for a method, which eliminates
 * the need to generate a jump around each catch block, which
 * would be a forward reference.
 */
final class CodeChunk {
	
	/**
	 * Starting point of the byte code stream in the underlying stream/array.
	 */
	private static   int CODE_OFFSET = 8;
		
	// The use of ILOAD for the non-integer types is correct.
	// We have to assume that the appropriate checks/conversions
	// are defined on math operation results to ensure that
	// the type is preserved when/as needed.
	static   short[] LOAD_VARIABLE = {
		 
	};

	static   short[] LOAD_VARIABLE_FAST = {
		 
	};

	// The ISTOREs for non-int types are how things work.
	// It assumes that the appropriate casts are done
	// on operations on non-ints to ensure that the values
	// remain in the valid ranges.
	static   short[] STORE_VARIABLE = {
		 
	};

	static   short[] STORE_VARIABLE_FAST = {
		 
	};

	static   short ARRAY_ACCESS[] = {
		 
	};
	static   short ARRAY_STORE[] = {
	 
	};	
	static   short[] RETURN_OPCODE = {
		 
		};

	// the first dimension is the current vmTypeId
	// the second dimension is the target vmTypeId
	//
	// the cells of the entry at [current,target] are:
	// 0: operation
	// 1: result type of operation
	// if entry[1] = target, we are done. otherwise,
	// you have to continue with entry[1] as the new current
	// after generating the opcode listed (don't generate if it is NOP).
	// if entry[0] = BAD, we can
	
	static final short CAST_CONVERSION_INFO[][][] = {
		/* current = vm_byte */
		{
		 
		},
		/* current = vm_short */
		{
		 
		},
		/* current = vm_int */
		{
	 
		},
		/* current = vm_long */
		{
		 
		},
		/* current = vm_float */
		{
		 
		},
		/* current = vm_double */
		{
		 
		},
		/* current = vm_char */
		{
		 
		},
		/* current = vm_reference */
		{
		 
		}
	};
	
	/**
	 * Constant used by OPCODE_ACTION to represent the
	 * common action of push one word, 1 byte
	 * for the instruction.
	 */
	private static final byte[] push1_1i = {1, 1};

	/**
	 * Constant used by OPCODE_ACTION to represent the
	 * common action of push two words, 1 byte
	 * for the instruction.
	 */
	private static final byte[] push2_1i = {2, 1};	
	/**
	 * Constant used by OPCODE_ACTION to the opcode is
	 * not yet supported.
	 */
	private static final byte[] NS = {0, -1};
	
	
	/**
	 * Value for OPCODE_ACTION[opcode][0] to represent
	 * the number of words popped or pushed in variable.
	 */
	private static final byte VARIABLE_STACK = -128;
	
	/**
	 * Array that provides two pieces of information about
	 * each VM opcode. Each opcode has a two byte array.
	 * <P>
	 * The first element in the array [0] is the number of
	 * stack words (double/long count as two) pushed by the opcode.
	 * Will be negative if the opcode pops values.
	 * 
	 * <P>
	 * The second element in the array [1] is the number of bytes
	 * in the instruction stream that this opcode's instruction
	 * takes up, including the opocode.
	 */
	private static final byte[][] OPCODE_ACTION =
	{
	
    /* NOP  0 */           { 0, 1 },
    
    /* ACONST_NULL  1 */  push1_1i,
    /* ICONST_M1  2 */    push1_1i,
    /* ICONST_0  3 */     push1_1i,
    /* ICONST_1  4 */     push1_1i,
    /* ICONST_2  5 */     push1_1i,
    /* ICONST_3  6 */     push1_1i,
    /* ICONST_4  7 */     push1_1i,
    /* ICONST_5  8 */     push1_1i,
    /* LCONST_0  9 */     push2_1i,
    /* LCONST_1  10 */    push2_1i,
    /* FCONST_0  11 */    push1_1i,
    /* FCONST_1  12 */    push1_1i,
    /* FCONST_2  13 */    push1_1i,
    /* DCONST_0  14 */    push2_1i,
    /* DCONST_1  15 */    push2_1i,
    
    /* BIPUSH  16 */     {1, 2},
    /* SIPUSH  17 */     {1, 3},
    /* LDC  18 */        {1, 2},
    /* LDC_W  19 */      {1, 3},
    /* LDC2_W  20 */     {2, 3},
    
    /* ILOAD  21 */     { 1, 2 },
    /* LLOAD  22 */     { 2, 2 },
    /* FLOAD  23 */     { 1, 2 },
    /* DLOAD  24 */     { 2, 2 },
    /* ALOAD  25 */     { 1, 2 },
    /* ILOAD_0  26 */   push1_1i,
    /* ILOAD_1  27 */   push1_1i,
    /* ILOAD_2  28 */   push1_1i,
    /* ILOAD_3  29 */   push1_1i,
    /* LLOAD_0  30 */   push2_1i,
    /* LLOAD_1  31 */   push2_1i,
    /* LLOAD_2  32 */   push2_1i,
    /* LLOAD_3  33 */   push2_1i,
    /* FLOAD_0  34 */   push1_1i,
    /* FLOAD_1  35 */   push1_1i,
    /* FLOAD_2  36 */   push1_1i,
    /* FLOAD_3  37 */   push1_1i,
    /* DLOAD_0  38 */   push2_1i,
    /* DLOAD_1  39 */   push2_1i,
    /* DLOAD_2  40 */   push2_1i,
    /* DLOAD_3  41 */   push2_1i,
    /* ALOAD_0  42 */   push1_1i,
    /* ALOAD_1  43 */   push1_1i,
    /* ALOAD_2  44 */   push1_1i,
    /* ALOAD_3  45 */   push1_1i,
    /* IALOAD  46 */    { -1, 1 },
    /* LALOAD  47 */    { 0, 1 },
    /* FALOAD  48 */    { -1, 1 },
    /* DALOAD  49 */    { 0, 1 },
    /* AALOAD  50 */    { -1, 1 },
    /* BALOAD  51 */    { -1, 1 },
    /* CALOAD  52 */    { -1, 1 },
    
    /* SALOAD  53 */       { -1, 1 },
    /* ISTORE  54 */       { -1, 2 },
    /* LSTORE  55 */       { -2, 2 },
    /* FSTORE  56 */       { -1, 2 },
    /* DSTORE  57 */       { -2, 2 },
    /* ASTORE  58 */       { -1, 2 },
    /* ISTORE_0  59 */     { -1, 1 },
    /* ISTORE_1  60 */     { -1, 1 },
    /* ISTORE_2  61 */     { -1, 1 },
    /* ISTORE_3  62 */     { -1, 1 },
    /* LSTORE_0  63 */     { -2, 1 },
    /* LSTORE_1  64 */     { -2, 1 },
    /* LSTORE_2  65 */     { -2, 1 },
    /* LSTORE_3  66 */     { -2, 1 },
    /* FSTORE_0  67 */     { -1, 1 },
    /* FSTORE_1  68 */     { -1, 1 },
    /* FSTORE_2  69 */     { -1, 1 },
    /* FSTORE_3  70 */     { -1, 1 },
    /* DSTORE_0  71 */     { -2, 1 },
    /* DSTORE_1  72 */     { -2, 1 },
    /* DSTORE_2  73 */     { -2, 1 },
    /* DSTORE_3  74 */     { -2, 1 },
    /* ASTORE_0  75 */     { -1, 1 },
    /* ASTORE_1  76 */     { -1, 1 },
    /* ASTORE_2  77 */     { -1, 1 },
    /* ASTORE_3  78 */     { -1, 1 },
    /* IASTORE  79 */      { -3, 1 },
    /* LASTORE  80 */      { -4, 1 },
    /* FASTORE  81 */      { -3, 1 },
    /* DASTORE  82 */      { -4, 1 },
    /* AASTORE  83 */      { -3, 1 },
    /* BASTORE  84 */      { -3, 1 },
    /* CASTORE  85 */      { -3, 1 },
    /* SASTORE  86 */      { -3, 1 },
    
    /* POP  87 */      { -1, 1 },
    /* POP2  88 */     { -2, 1 },
    /* DUP  89 */      push1_1i,
    /* DUP_X1  90 */   push1_1i,
    /* DUP_X2  91 */   push1_1i,
    /* DUP2  92 */     push2_1i,
    /* DUP2_X1  93 */  push2_1i,
    /* DUP2_X2  94 */  push2_1i,
    /* SWAP  95 */     { 0, 1 },
    
    /* IADD  96 */     NS,
    /* LADD  97 */     NS,
    /* FADD  98 */     { -1, 1 },
    /* DADD  99 */     { -2, 1 },
    /* ISUB  100 */     NS,
    /* LSUB  101 */     NS,
    /* FSUB  102 */     { -1, 1 },
    /* DSUB  103 */     { -2, 1 },
    /* IMUL  104 */     NS,
    /* LMUL  105 */     NS,
    /* FMUL  106 */     { -1, 1 },
    /* DMUL  107 */     { -2, 1 },
    /* IDIV  108 */     NS,
    /* LDIV  109 */     NS,
    /* FDIV  110 */     { -1, 1 },
    /* DDIV  111 */     { -2, 1 },
    /* IREM  112 */     { -1, 1 },
    /* LREM  113 */     { -2, 1 },
    /* FREM  114 */     { -1, 1 },
    /* DREM  115 */     { -2, 1 },
    /* INEG  116 */     { 0, 1 },
    /* LNEG  117 */     { 0, 1 },
    /* FNEG  118 */     { 0, 1 },
    /* DNEG  119 */     { 0, 1 },
    /* ISHL  120 */     { -1, 1 },
    /* LSHL  121 */     NS,
    /* ISHR  122 */     NS,
    /* LSHR  123 */     NS,
    /* IUSHR  124 */     NS,
    /* LUSHR  125 */     NS,
    
    /* IAND  126 */     { -1, 1 },
    /* LAND  127 */     NS,
    /* IOR  128 */      { -1, 1 },
    /* LOR  129 */      NS,
    /* IXOR  130 */     NS,
    /* LXOR  131 */     NS,
    /* IINC  132 */     NS,
    
    /* I2L  133 */     push1_1i,
    /* I2F  134 */     { 0, 1 },
    /* I2D  135 */     push1_1i,
    /* L2I  136 */     { -1, 1 },
    /* L2F  137 */     { -1, 1 },
    /* L2D  138 */     { 0, 1 },
    /* F2I  139 */     { 0, 1 },
    /* F2L  140 */     push2_1i,
    /* F2D  141 */     push1_1i,
    /* D2I  142 */     { -1, 1 },
    /* D2L  143 */     { 0, 1 },
    /* D2F  144 */     { -1, 1 },
    /* I2B  145 */     { 0, 1 },
    /* I2C  146 */     { 0, 1 },
    /* I2S  147 */     { 0, 1 },
    
    /* LCMP  148 */        NS,
    /* FCMPL  149 */       { -1, 1 },
    /* FCMPG  150 */       { -1, 1 },
    /* DCMPL  151 */       { -3, 1 },
    /* DCMPG  152 */       { -3, 1 }, 
    /* IF_ICMPEQ  159 */   NS,
    /* IF_ICMPNE  160 */   NS,
    /* IF_ICMPLT  161 */   NS,
    /* IF_ICMPGE  162 */   NS,
    /* IF_ICMPGT  163 */   NS,
    /* IF_ICMPLE  164 */   NS,
    /* IF_ACMPEQ  165 */   NS,
    /* IF_ACMPNE  166 */   NS, 
    /* JSR  168 */         NS,
    /* RET  169 */         NS,
    /* TABLESWITCH  170 */ NS,
    /* LOOKUPSWITCH  171 */NS,
    
    /* IRETURN  172 */     { -1, 1 }, // strictly speaking all words on the stack are popped.
    /* LRETURN  173 */     { -2, 1 }, // strictly speaking all words on the stack are popped.
    /* FRETURN  174 */     { -1, 1 }, // strictly speaking all words on the stack are popped.
    /* DRETURN  175 */     { -2, 1 }, // strictly speaking all words on the stack are popped.
    /* ARETURN  176 */     { -1, 1 }, // strictly speaking all words on the stack are popped.
    /* RETURN  177 */      { 0, 1 }, // strictly speaking all words on the stack are popped.

    /* GETSTATIC  178 */           {VARIABLE_STACK, 3 },
    /* PUTSTATIC  179 */           {VARIABLE_STACK, 3 },
    /* GETFIELD  180 */            {VARIABLE_STACK, 3 },
    /* PUTFIELD  181 */            {VARIABLE_STACK, 3 },
    /* INVOKEVIRTUAL  182 */       {VARIABLE_STACK, 3 },
    /* INVOKESPECIAL  183 */       {VARIABLE_STACK, 3 },
    /* INVOKESTATIC  184 */        {VARIABLE_STACK, 3 },
    /* INVOKEINTERFACE  185 */     {VARIABLE_STACK, 5 },
    
    /* XXXUNUSEDXXX  186 */        NS,

    /* NEW  187 */                 { 1, 3 },
    /* NEWARRAY  188 */            { 0, 2 },
    /* ANEWARRAY  189 */           { 0, 3 },
    /* ARRAYLENGTH  190 */         { 0, 1 },
    /* ATHROW  191 */              NS,
    /* CHECKCAST  192 */           { 0, 3},
    /* INSTANCEOF  193 */          { 0, 3 },
    /* MONITORENTER  194 */        NS,
    /* MONITOREXIT  195 */         NS,
    /* WIDE  196 */                NS,
    /* MULTIANEWARRAY  197 */      NS, 
    /* JSR_W  201 */               NS,
    /* BREAKPOINT  202 */          NS,
	
	};
	
    /**
     * Assume an IOException means some limit of the class file
     * format was hit
     * 
     */
    private void limitHit(IOException ioe)
    {
        cb.addLimitExceeded(ioe.toString());
    }
	
	
	/**
	 * Add an instruction that has no operand.
	 * All opcodes are 1 byte large.
	 */
	void addInstr(short opcode) {
		 

		if (SanityManager.DEBUG) {			
			if (OPCODE_ACTION[opcode][1] != 1)
				SanityManager.THROWASSERT("Opcode " + opcode + " incorrect entry in OPCODE_ACTION -" +
						" writing 1 byte - set as " + OPCODE_ACTION[opcode][1]);		
		}
	}

	/**
	 * Add an instruction that has a 16 bit operand.
	 */
	void addInstrU2(short opcode, int operand) {
        
		 

		if (SanityManager.DEBUG) {			
			if (OPCODE_ACTION[opcode][1] != 3)
				SanityManager.THROWASSERT("Opcode " + opcode + " incorrect entry in OPCODE_ACTION -" +
						" writing 3 bytes - set as " + OPCODE_ACTION[opcode][1]);		
		}
	}

	/**
	 * Add an instruction that has a 32 bit operand.
	 */
     void addInstrU4(short opcode, int operand) {
		 
		if (SanityManager.DEBUG) {			
			if (OPCODE_ACTION[opcode][1] != 5)
				SanityManager.THROWASSERT("Opcode " + opcode + " incorrect entry in OPCODE_ACTION -" +
						" writing 5 bytes - set as " + OPCODE_ACTION[opcode][1]);		
		}
	}

     
 	/**
 	 * Add an instruction that has an 8 bit operand.
 	 */
     void addInstrU1(short opcode, int operand) {
	 

		// Only debug code from here.
		if (SanityManager.DEBUG) {
			
			if (OPCODE_ACTION[opcode][1] != 2)
				SanityManager.THROWASSERT("Opcode " + opcode + " incorrect entry in OPCODE_ACTION -" +
						" writing 2 bytes - set as " + OPCODE_ACTION[opcode][1]);
		
		}
	}

	/**
	 * This takes an instruction that has a narrow
	 * and a wide form for CPE access, and
	 * generates accordingly the right one.
	 * We assume the narrow instruction is what
	 * we were given, and that the wide form is
	 * the next possible instruction.
	 */
	void addInstrCPE(short opcode, int cpeNum) {
		if (cpeNum < 256) {
			addInstrU1(opcode, cpeNum);
		}
		else {
			addInstrU2((short) (opcode+1), cpeNum);
		}
	}

	/**
	 * This takes an instruction that can be wrapped in
	 * a wide for large variable #s and does so.
	 */
	void addInstrWide(short opcode, int varNum) {
		 
	}

	/**
	 * For adding an instruction with 3 operands, a U2 and two U1's.
	 * So far, this is used by VMOpcode.INVOKEINTERFACE.
	 */
	void addInstrU2U1U1(short opcode, int operand1, short operand2){
		 
	}

	/** Get the current program counter */
	int getPC() {
		return 0;
	}
	
	/**
	 * Return the complete instruction length for the
	 * passed in opcode. This will include the space for
	 * the opcode and its operand.
	 */
	private static int instructionLength(short opcode)
	{
		int instructionLength = OPCODE_ACTION[opcode][1];
		
		if (SanityManager.DEBUG)
		{
			if (instructionLength < 0)
				SanityManager.THROWASSERT("Opcode without instruction length " + opcode);
		}
		
		return instructionLength;
	}
	
	/**
	 * The delta between cout.size() and the pc.
	 * For an initial code chunk this is -8 (CODE_OFFSET)
	 * since 8 bytes are written.
	 * For a nested CodeChunk return by insertCodeSpace the delta
	 * corresponds to the original starting pc.
	 * @see #insertCodeSpace
	 */
	private final int pcDelta;
    
    /**
     * The class we are generating code for, used to indicate that
     * some limit was hit during code generation.
     */
    final BCClass       cb;

	CodeChunk(BCClass cb) {
        this.cb = cb;
        
		pcDelta = - CodeChunk.CODE_OFFSET;
	}
	
	/**
	 * Return a CodeChunk that has limited visibility into
	 * this CodeChunk. Used when a caller needs to insert instructions
	 * into an existing stream.
	 * @param pc
	 * @param byteCount
	 */
	private CodeChunk(CodeChunk main, int pc, int byteCount)
	{ 
		this.cb = null;
        
		pcDelta = pc;
	}

 

	/**
	 * now that we have codeBytes, fix the lengths fields in it
	 * to reflect what was stored.
	 * Limits checked here are from these sections of the JVM spec.
	 * <UL>
	 * <LI> 4.7.3 The Code Attribute
	 * <LI> 4.10 Limitations of the Java Virtual Machine 
	 * </UL>
	 */
	private void fixLengths(BCMethod mb, int maxStack, int maxLocals, int codeLength) {

	  
	}

	 
	/**
	 * Return the opcode at the given pc.
	 */
	short getOpcode(int pc)
	{
		 return 0;
	}
	
	/**
	 * Get the unsigned short value for the opcode at the program
	 * counter pc.
	 */
	private int getU2(int pc)
	{
		 return 0;
	}

	/**
	 * Get the unsigned 32 bit value for the opcode at the program
	 * counter pc.
	 */
	private int getU4(int pc)
	{
		 return 0;
	}	
	/**
	 * Insert room for byteCount bytes after the instruction at pc
	 * and prepare to replace the instruction at pc. The instruction
	 * at pc is not modified by this call, space is allocated after it.
	 * The newly inserted space will be filled with NOP instructions.
	 * 
	 * Returns a CodeChunk positioned at pc and available to write
	 * instructions upto (byteCode + length(existing instruction at pc) bytes.
	 * 
	 * This chunk is left correctly positioned at the end of the code
	 * stream, ready to accept more code. Its pc will have increased by
	 * additionalBytes.
	 * 
	 * It is the responsibility of the caller to patch up any
	 * branches or gotos.
	 * 
	 * @param pc
	 * @param additionalBytes
	 */
	CodeChunk insertCodeSpace(int pc, int additionalBytes)
	{
		 
		
		return new CodeChunk(this, pc, additionalBytes);
						
	}
	
	/*
     * * Methods related to splitting the byte code chunks into sections that
     * fit in the JVM's limits for a single method.
     */

    
  
    /**
     * Get the word count for a type descriptor in the format of the virual
     * machine. For a method this returns the the word count for the return
     * type.
     */
    private static int getDescriptorWordCount(String vmDescriptor) {
 
        int width=0;
         

        return width;
    }

     

    /**
     * Calculate the number of stack words in the arguments pushed for this
     * method descriptor.
     */
    private static int parameterWordCount(String methodDescriptor) {
        int wordCount = 0;
       return 0;
    }

    /**
     * Find the limits of a conditional block starting at the instruction with
     * the given opcode at the program counter pc.
     * <P>
     * Returns a six element integer array of program counters and lengths.
     * <code. [0] - program counter of the IF opcode (passed in as pc) [1] -
     * program counter of the start of the then block [2] - length of the then
     * block [3] - program counter of the else block, -1 if no else block
     * exists. [4] - length of of the else block, -1 if no else block exists.
     * [5] - program counter of the common end point. </code>
     * 
     * Looks for and handles conditionals that are written by the Conditional
     * class.
     * 
     * @return Null if the opcode is not the start of a conditional otherwise
     *         the array of values.
     */
    private int[] findConditionalPCs(int pc, short opcode) {
         
        int[] ret = new int[6];

        ret[0] = pc;
        

        return ret;
    }
    
     

    /**
     * Start a sub method that we will split the portion of our current code to,
     * starting from start_pc and including codeLength bytes of code.
     * 
     * Return a BCMethod obtained from BCMethod.getNewSubMethod with the passed
     * in return type and same parameters as mb if the code block to be moved
     * uses parameters.
     */
    private BCMethod startSubMethod(BCMethod mb, String returnType,
            int split_pc, int blockLength) {

        boolean needParameters = usesParameters(mb, split_pc, blockLength);

        return mb.getNewSubMethod(returnType, needParameters);
    }

    /**
     * Does a section of code use parameters.
     * Any load, exception ALOAD_0 in an instance method, is
     * seen as using parameters, as this complete byte code
     * implementation does not use local variables.
     * 
     */
    private boolean usesParameters(BCMethod mb, int pc, int codeLength) {

         
        return false;
    }
        
    
    /**
     * Minimum split length for a sub-method. If the number of
     * instructions to call the sub-method exceeds the length
     * of the sub-method, then there's no point splitting.
     * The number of bytes in the code stream to call
     * a generated sub-method can take is based upon the number of method args.
     * A method can have maximum of 255 words of arguments (section 4.10 JVM spec)
     * which in the worst case would be 254 (one-word) parameters
     * and this. For a sub-method the arguments will come from the
     * parameters to the method, i.e. ALOAD, ILOAD etc.
     * <BR>
     * This leads to this number of instructions.
     * <UL>
     * <LI> 4 - 'this' and first 3 parameters have single byte instructions
     * <LI> (N-4)*2 - Remaining parameters have two byte instructions
     * <LI> 3 for the invoke instruction.
     * </UL>
     */
    private static int splitMinLength(BCMethod mb) {
        int min = 1 + 3; // For ALOAD_0 (this) and invoke instruction
        
        if (mb.parameters != null) {
            int paramCount = mb.parameters.length;
            
            min += paramCount;
            
            if (paramCount > 3)
                min += (paramCount - 3);
        }
        
        return min;
    }
    /*
    final int splitNonZeroStack(BCMethod mb, ClassHolder ch,
            final int codeLength, final int optimalMinLength,
            int maxStack) {
        
        // program counter for the instruction that
        // made the stack reach the given stack depth.
        int[] stack_pcs = new int[maxStack+1];
        Arrays.fill(stack_pcs, -1);
        
        int stack = 0;
        
        // maximum possible split seen that is less than
        // the minimum.
        int possibleSplitLength = -1;
        
        System.out.println("NZ SPLIT + " + mb.getName());

        // do not split until at least this point (inclusive)
        // used to ensure no split occurs in the middle of
        // a conditional.
        int outerConditionalEnd_pc = -1;

        int end_pc = 0 + codeLength;
        for (int pc = 0; pc < end_pc;) {

            short opcode = getOpcode(pc);

            int stackDelta = stackWordDelta(ch, pc, opcode);
            
            stack += stackDelta;
            
            // Cannot split a conditional but need to calculate
            // the stack depth at the end of the conditional.
            // Each path through the conditional will have the
            // same stack depth.
            int[] cond_pcs = findConditionalPCs(pc, opcode);
            if (cond_pcs != null) {
                // an else block exists, skip the then block.
                if (cond_pcs[3] != -1) {
                    pc = cond_pcs[3];
                    continue;
                }
                
                if (SanityManager.DEBUG)
                {
                    if (outerConditionalEnd_pc != -1)
                    {
                        if (cond_pcs[5] >= outerConditionalEnd_pc)
                            SanityManager.THROWASSERT("NESTED CONDITIONALS!");
                    }
                }

                if (outerConditionalEnd_pc == -1)
                {
                    outerConditionalEnd_pc = cond_pcs[5];
                }
            }
                       
            pc += instructionLength(opcode);
            
            // Don't split in the middle of a conditional
            if (outerConditionalEnd_pc != -1) {
                if (pc > outerConditionalEnd_pc) {
                    // passed the outermost conditional
                    outerConditionalEnd_pc = -1;
                }
                continue;
            }
            
            if (stackDelta == 0)
                continue;

            // Only split when the stack is having items popped
            if (stackDelta > 0)
            {
                // pushing double word, clear out a
                if (stackDelta == 2)
                    stack_pcs[stack - 1] = pc;
                stack_pcs[stack] = pc;
                continue;
            }
            
            int opcode_pc = pc - instructionLength(opcode);
            
            // Look for specific opcodes that have the capability
            // of having a significant amount of code in a self
            // contained block.
            switch (opcode)
            {
            // this.method(A) construct
            //  ...         -- stack N
            //  push this -- stack N+1
            //  push args -- stack N+1+A
            //  call method -- stack N+R (R=0,1,2)
            //
            //  stackDelta = (N+R) - (N+1+A) = R-(1+A)
            //  stack = N+R
            //  Need to determine N+1
            //  
            //  
            //
            //  this.a(<i2>, <i2>, <i3>)
            //  returning int
            //
            //  stackDelta = -3 (this & 3 args popped, ret pushed)
            //  initial depth N = 10
            //  pc        - stack
            //  100 ...       - stack 10
            //  101 push this - stack 11
            //  109 push i1   - stack 12
            //  125 push i2   - stack 13
            //  156 push i3   - stack 14
            //  157 call      - stack 11
            //  
            //  need stack_pcs[11] = stack_pcs[11 + -3]
            //
            // ref.method(args).method(args) ... method(args)
            // 
            case VMOpcode.INVOKEINTERFACE:
            case VMOpcode.INVOKESPECIAL:
            case VMOpcode.INVOKEVIRTUAL:
            {
                String vmDescriptor = getTypeDescriptor(ch, opcode_pc);
                int r = CodeChunk.getDescriptorWordCount(vmDescriptor);
             
                // PC of the opcode that pushed the reference for
                // this method call.
                int ref_pc = stack_pcs[stack - r + 1];
               if (getOpcode(ref_pc) == VMOpcode.ALOAD_0) {
                    System.out.println("POSS SPLIT " + (pc - ref_pc) + " @ " + ref_pc);
                }
               break;
            }
            case VMOpcode.INVOKESTATIC:
                String vmDescriptor = getTypeDescriptor(ch, opcode_pc);
                int r = CodeChunk.getDescriptorWordCount(vmDescriptor);
                int p1_pc = stack_pcs[stack - r + 1];
                System.out.println("POSS STATIC SPLIT " + (pc - p1_pc) + " @ " + p1_pc);
                
            }
            stack_pcs[stack] = opcode_pc;
        }
        return -1;
    }*/
}
