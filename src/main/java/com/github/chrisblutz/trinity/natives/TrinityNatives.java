package com.github.chrisblutz.trinity.natives;

import com.github.chrisblutz.trinity.lang.ClassRegistry;
import com.github.chrisblutz.trinity.lang.TYClass;
import com.github.chrisblutz.trinity.lang.TYMethod;
import com.github.chrisblutz.trinity.lang.TYObject;
import com.github.chrisblutz.trinity.lang.errors.Errors;
import com.github.chrisblutz.trinity.lang.errors.stacktrace.TYStackTrace;
import com.github.chrisblutz.trinity.lang.procedures.ProcedureAction;
import com.github.chrisblutz.trinity.lang.procedures.TYProcedure;
import com.github.chrisblutz.trinity.lang.scope.TYRuntime;
import com.github.chrisblutz.trinity.lang.types.arrays.TYArray;
import com.github.chrisblutz.trinity.lang.types.bool.TYBoolean;
import com.github.chrisblutz.trinity.lang.types.numeric.TYFloat;
import com.github.chrisblutz.trinity.lang.types.numeric.TYInt;
import com.github.chrisblutz.trinity.lang.types.numeric.TYLong;
import com.github.chrisblutz.trinity.lang.types.strings.TYString;

import java.util.*;


/**
 * This class houses the central API for native
 * methods in Trinity.
 *
 * @author Christopher Lutz
 */
public class TrinityNatives {
    
    private static Map<String, List<NativeAction>> pendingActions = new HashMap<>();
    private static Map<String, TYMethod> methods = new HashMap<>();
    private static Map<String, TYClass> pendingLoads = new HashMap<>();
    private static Map<String, Boolean> pendingSecure = new HashMap<>();
    private static Map<String, String> pendingLoadFiles = new HashMap<>();
    private static Map<String, Integer> pendingLoadLines = new HashMap<>();
    
    private static Map<String, ProcedureAction> globals = new HashMap<>();
    
    public static void registerMethod(String className, String methodName, boolean staticMethod, String[] mandatoryParams, Map<String, ProcedureAction> optionalParams, String blockParam, ProcedureAction action) {
        
        ProcedureAction actionWithStackTrace = (runtime, thisObj, params) -> {
            
            TYStackTrace.add(className, methodName, null, 0);
            
            TYObject result = action.onAction(runtime, thisObj, params);
            
            TYStackTrace.pop();
            
            return result;
        };
        
        List<String> mandatoryParamsList;
        if (mandatoryParams != null) {
            
            mandatoryParamsList = Arrays.asList(mandatoryParams);
            
        } else {
            
            mandatoryParamsList = new ArrayList<>();
        }
        
        if (optionalParams == null) {
            
            optionalParams = new TreeMap<>();
        }
        
        TYProcedure procedure = new TYProcedure(actionWithStackTrace, mandatoryParamsList, optionalParams, blockParam);
        TYMethod method = new TYMethod(methodName, staticMethod, true, ClassRegistry.getClass(className), procedure);
        String fullName = className + "." + methodName;
        methods.put(fullName, method);
    }
    
    public static void registerMethodPendingLoad(String pendingClassName, String className, String methodName, boolean staticMethod, String[] mandatoryParams, Map<String, ProcedureAction> optionalParams, String blockParam, ProcedureAction action) {
        
        performPendingLoad(pendingClassName, () -> registerMethod(className, methodName, staticMethod, mandatoryParams, optionalParams, blockParam, action));
    }
    
    public static void registerGlobal(String name, ProcedureAction action) {
        
        globals.put(name, action);
    }
    
    public static void registerGlobalPendingLoad(String pendingClassName, String name, ProcedureAction action) {
        
        performPendingLoad(pendingClassName, () -> registerGlobal(name, action));
    }
    
    public static void performPendingLoad(String className, NativeAction action) {
        
        if (ClassRegistry.classExists(className)) {
            
            action.onAction();
            
        } else {
            
            if (!pendingActions.containsKey(className)) {
                
                pendingActions.put(className, new ArrayList<>());
            }
            
            pendingActions.get(className).add(action);
        }
    }
    
    public static void doLoad(String name, boolean secureMethod, TYClass current, String fileName, int lineNumber) {
        
        if (methods.containsKey(name)) {
            
            addToClass(name, secureMethod, current, fileName, lineNumber);
            
        } else {
            
            pendingLoads.put(name, current);
            pendingSecure.put(name, secureMethod);
            pendingLoadFiles.put(name, fileName);
            pendingLoadLines.put(name, lineNumber);
        }
    }
    
    private static void addToClass(String name, boolean secureMethod, TYClass current, String fileName, int lineNumber) {
        
        if (methods.containsKey(name)) {
            
            TYMethod method = methods.get(name);
            method.setSecureMethod(secureMethod);
            current.registerMethod(method);
            
        } else {
            
            Errors.throwError("Trinity.Errors.ParseError", "Native method " + name + " not found.", fileName, lineNumber);
        }
    }
    
    public static void triggerActionsPendingLoad(String className) {
        
        if (pendingActions.containsKey(className)) {
            
            for (NativeAction action : pendingActions.get(className)) {
                
                action.onAction();
            }
        }
        
        for (String str : pendingLoads.keySet()) {
            
            if (methods.containsKey(str)) {
                
                addToClass(str, pendingSecure.get(str), pendingLoads.get(str), pendingLoadFiles.get(str), pendingLoadLines.get(str));
                pendingLoads.remove(str);
                pendingSecure.remove(str);
                pendingLoadFiles.remove(str);
                pendingLoadLines.remove(str);
            }
        }
    }
    
    public static ProcedureAction getGlobalProcedureAction(String name) {
        
        return globals.get(name);
    }
    
    /**
     * This method wraps native types inside Trinity's
     * object format ({@code TYObject}).
     * <br>
     * <br>
     * Possible Types:<br>
     * - Integer<br>
     * - Float/Double<br>
     * - Long<br>
     * - String<br>
     * - Boolean<br>
     *
     * @param obj The object to be converted into a native Trinity object
     * @return The converted {@code TYObject} form of the original object
     */
    public static TYObject getObjectFor(Object obj) {
        
        if (obj == null) {
            
            return TYObject.NIL;
            
        } else if (obj instanceof Integer) {
            
            return new TYInt((Integer) obj);
            
        } else if (obj instanceof Float) {
            
            return new TYFloat((Float) obj);
            
        } else if (obj instanceof Double) {
            
            return new TYFloat((Double) obj);
            
        } else if (obj instanceof Long) {
            
            return new TYLong((Long) obj);
            
        } else if (obj instanceof String) {
            
            return new TYString((String) obj);
            
        } else if (obj instanceof Boolean) {
            
            if ((Boolean) obj) {
                
                return TYBoolean.TRUE;
                
            } else {
                
                return TYBoolean.FALSE;
            }
        }
        
        Errors.throwError("Trinity.Errors.NativeTypeError", "Trinity does not have native type-conversion utilities for " + obj.getClass() + ".", null, 0);
        
        return TYObject.NIL;
    }
    
    
    /**
     * This method wraps an array of native types inside Trinity's
     * array format ({@code TYObject}).  All objects inside are converted
     * into Trinity's native type of them.
     * <br>
     * <br>
     * Possible Types:<br>
     * - Integer<br>
     * - Float/Double<br>
     * - Long<br>
     * - String<br>
     * - Boolean<br>
     *
     * @param arr The array of Objects to be converted into a native array of Trinity object
     * @return The converted {@code TYArray} form of the original form, containing {@code TYObject} forms of
     * the original objects
     */
    public static TYArray getArrayFor(Object[] arr) {
        
        List<TYObject> objects = new ArrayList<>();
        
        for (Object o : arr) {
            
            objects.add(getObjectFor(o));
        }
        
        return new TYArray(objects);
    }
    
    public static TYObject newInstance(String className, TYObject... args) {
        
        return newInstance(className, new TYRuntime(), args);
    }
    
    public static TYObject newInstance(String className, TYRuntime runtime, TYObject... args) {
        
        return ClassRegistry.getClass(className).tyInvoke("new", runtime, null, null, TYObject.NONE, args);
    }
    
    public static TYObject call(String className, String methodName, TYRuntime runtime, TYObject thisObj, TYObject... args) {
        
        if (ClassRegistry.classExists(className)) {
            
            return ClassRegistry.getClass(className).tyInvoke(methodName, runtime, null, null, thisObj, args);
            
        } else {
            
            Errors.throwError("Trinity.Errors.ClassNotFoundError", "Class " + className + " does not exist.", runtime);
        }
        
        return TYObject.NIL;
    }
    
    public static <T extends TYObject> T cast(Class<T> desiredClass, TYObject object) {
        
        if (desiredClass.isInstance(object)) {
            
            return desiredClass.cast(object);
            
        } else {
            
            Errors.throwError("Trinity.Errors.InvalidTypeError", "Unexpected value of type " + object.getObjectClass().getName() + " found.");
            
            // This will throw an error, but the program will exit at the line above, never reaching this point
            return desiredClass.cast(object);
        }
    }
    
    public static int toInt(TYObject tyObject) {
        
        if (tyObject instanceof TYLong) {
            
            return Math.toIntExact(((TYLong) tyObject).getInternalLong());
            
        } else if (tyObject instanceof TYFloat) {
            
            return (int) ((TYFloat) tyObject).getInternalDouble();
            
        } else if (tyObject instanceof TYString) {
            
            return Integer.parseInt(((TYString) tyObject).getInternalString());
            
        } else {
            
            return TrinityNatives.cast(TYInt.class, tyObject).getInternalInteger();
        }
    }
    
    public static long toLong(TYObject tyObject) {
        
        if (tyObject instanceof TYInt) {
            
            return ((TYInt) tyObject).getInternalInteger();
            
        } else if (tyObject instanceof TYFloat) {
            
            return (long) ((TYFloat) tyObject).getInternalDouble();
            
        } else if (tyObject instanceof TYString) {
            
            return Long.parseLong(((TYString) tyObject).getInternalString());
            
        } else {
            
            return TrinityNatives.cast(TYLong.class, tyObject).getInternalLong();
        }
    }
    
    public static double toFloat(TYObject tyObject) {
        
        if (tyObject instanceof TYInt) {
            
            return ((TYInt) tyObject).getInternalInteger();
            
        } else if (tyObject instanceof TYLong) {
            
            return ((TYLong) tyObject).getInternalLong();
            
        } else if (tyObject instanceof TYString) {
            
            return Double.parseDouble(((TYString) tyObject).getInternalString());
            
        } else {
            
            return TrinityNatives.cast(TYFloat.class, tyObject).getInternalDouble();
        }
    }
    
    public static String toString(TYObject tyObject, TYRuntime runtime) {
        
        if (tyObject instanceof TYString) {
            
            return ((TYString) tyObject).getInternalString();
            
        } else {
            
            TYString tyString = cast(TYString.class, tyObject.tyInvoke("toString", runtime, null, null));
            return tyString.getInternalString();
        }
    }
    
    public static boolean toBoolean(TYObject tyObject) {
        
        if (tyObject == TYObject.NIL || tyObject == TYObject.NONE) {
            
            return false;
            
        } else if (tyObject instanceof TYBoolean) {
            
            return ((TYBoolean) tyObject).getInternalBoolean();
            
        } else if (tyObject instanceof TYInt) {
            
            return ((TYInt) tyObject).getInternalInteger() != 0;
            
        } else if (tyObject instanceof TYLong) {
            
            return ((TYLong) tyObject).getInternalLong() != 0;
            
        } else if (tyObject instanceof TYFloat) {
            
            return ((TYFloat) tyObject).getInternalDouble() != 0;
            
        } else {
            
            return true;
        }
    }
}
