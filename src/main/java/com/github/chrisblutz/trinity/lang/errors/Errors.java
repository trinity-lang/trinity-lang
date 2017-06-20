package com.github.chrisblutz.trinity.lang.errors;

import com.github.chrisblutz.trinity.Trinity;
import com.github.chrisblutz.trinity.cli.CLI;
import com.github.chrisblutz.trinity.interpreter.errors.TrinityErrorException;
import com.github.chrisblutz.trinity.lang.TYObject;
import com.github.chrisblutz.trinity.lang.scope.TYRuntime;
import com.github.chrisblutz.trinity.lang.types.strings.TYString;
import com.github.chrisblutz.trinity.natives.TrinityNatives;


/**
 * @author Christopher Lutz
 */
public class Errors {
    
    public static void throwError(String errorClass, Object... args) {
        
        throwError(errorClass, new TYRuntime(), args);
    }
    
    public static void throwError(String errorClass, TYRuntime runtime, Object... args) {
        
        TYObject error = constructError(errorClass, runtime, args);
        TrinityNatives.call("Trinity.Kernel", "throw", runtime, TYObject.NONE, error);
    }
    
    public static void throwSyntaxError(String errorClass, String message, String filename, int line) {
        
        // Mimic toString() method of Error class
        String str = errorClass;
        
        if (message != null && !message.isEmpty()) {
            
            str += ": " + message;
        }
        
        if (filename != null && line > 0) {
            
            str += "\n\tin '" + filename + "' at line " + line;
        }
        
        System.err.println(str);
        
        Trinity.exit(1);
    }
    
    public static void throwUnrecoverable(String errorClass, Object... args) {
        
        TYObject error = constructError(errorClass, new TYRuntime(), args);
        throwUncaughtJavaException(new TrinityErrorException(error), null, 0);
    }
    
    private static TYObject constructError(String errorClass, TYRuntime runtime, Object... args) {
        
        TYObject[] tyArgs = new TYObject[args.length];
        for (int i = 0; i < args.length; i++) {
            
            Object o = args[i];
            
            if (o instanceof TYObject) {
                
                tyArgs[i] = (TYObject) o;
                
            } else {
                
                tyArgs[i] = TrinityNatives.getObjectFor(o);
            }
        }
        
        return TrinityNatives.newInstance(errorClass, runtime, tyArgs);
    }
    
    public static void throwUncaughtJavaException(Throwable error, String file, int line) {
        
        if (error instanceof TrinityErrorException) {
            
            TYObject tyError = ((TrinityErrorException) error).getErrorObject();
            String errorMessage = TrinityNatives.cast(TYString.class, tyError.tyInvoke("toString", new TYRuntime(), null, null)).getInternalString();
            
            System.err.println(errorMessage);
            
        } else if (error instanceof StackOverflowError) {
            
            throwUnrecoverable("Trinity.Errors.StackOverflowError");
            
        } else {
            
            System.err.println("An error occurred in the Trinity interpreter in file '" + file + "' at line " + line + ".");
            
            if (CLI.isDebuggingEnabled()) {
                
                System.err.println("\n== FULL ERROR ==\n");
                error.printStackTrace();
                
            } else {
                
                System.err.println("To view a full trace, enable debugging with the -d/--debug option.");
            }
        }
    }
}
