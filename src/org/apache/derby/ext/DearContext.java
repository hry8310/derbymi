package org.apache.derby.ext;

import org.apache.dearbaby.impl.sql.compile.CompilerContextImpl;
import org.apache.dearbaby.impl.sql.compile.ParserImpl;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.context.ContextService;
import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.compile.Parser;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
 

public class DearContext {
	    public static  LanguageConnectionContext lcc = null;
		public static  ContextManager cmng = null;
		public  static CompilerContext ctx = null;
		public static Parser getParser(){
			try{
			 
			 
				DearContext.cmng = new ContextManager(null);
			
			 
				DearContext.ctx = new CompilerContextImpl(  DearContext.cmng, null, null);
				return new ParserImpl( DearContext.ctx);
			}catch(Exception e){
				e.printStackTrace();
			}
			return null;
		}
}
