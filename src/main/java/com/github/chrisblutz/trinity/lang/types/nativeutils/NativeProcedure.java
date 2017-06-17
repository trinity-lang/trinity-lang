package com.github.chrisblutz.trinity.lang.types.nativeutils;

import com.github.chrisblutz.trinity.lang.TYObject;
import com.github.chrisblutz.trinity.lang.procedures.TYProcedure;
import com.github.chrisblutz.trinity.lang.scope.TYRuntime;
import com.github.chrisblutz.trinity.lang.types.arrays.TYArray;
import com.github.chrisblutz.trinity.lang.types.procedures.TYProcedureObject;
import com.github.chrisblutz.trinity.natives.NativeStorage;
import com.github.chrisblutz.trinity.natives.TrinityNatives;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Christopher Lutz
 */
class NativeProcedure {
    
    static void register() {
        
        TrinityNatives.registerMethod("Trinity.Procedure", "initialize", false, null, null, "block", null, (runtime, thisObj, params) -> {
            
            if (runtime.hasVariable("block")) {
                
                return runtime.getVariable("block");
                
            } else {
                
                return new TYProcedureObject(new TYProcedure((runtime11, thisObj1, params1) -> TYObject.NIL), new TYRuntime());
            }
        });
        TrinityNatives.registerMethod("Trinity.Procedure", "getRequiredArguments", false, null, null, null, null, (runtime, thisObj, params) -> NativeStorage.getMandatoryArguments(TrinityNatives.cast(TYProcedureObject.class, thisObj).getInternalProcedure()));
        TrinityNatives.registerMethod("Trinity.Procedure", "getOptionalArguments", false, null, null, null, null, (runtime, thisObj, params) -> NativeStorage.getOptionalArguments(TrinityNatives.cast(TYProcedureObject.class, thisObj).getInternalProcedure()));
        TrinityNatives.registerMethod("Trinity.Procedure", "getBlockArgument", false, null, null, null, null, (runtime, thisObj, params) -> NativeStorage.getBlockArgument(TrinityNatives.cast(TYProcedureObject.class, thisObj).getInternalProcedure()));
        TrinityNatives.registerMethod("Trinity.Procedure", "getOverflowArgument", false, null, null, null, null, (runtime, thisObj, params) -> NativeStorage.getOverflowArgument(TrinityNatives.cast(TYProcedureObject.class, thisObj).getInternalProcedure()));
        TrinityNatives.registerMethod("Trinity.Procedure", "call", false, null, null, null, "args", (runtime, thisObj, params) -> {
            
            TYProcedureObject obj = (TYProcedureObject) thisObj;
            TYProcedure proc = obj.getInternalProcedure();
            TYRuntime newRuntime = obj.getProcedureRuntime().clone();
            
            TYObject args = runtime.getVariable("args");
            List<TYObject> procParams = new ArrayList<>();
            if (args instanceof TYArray) {
                
                procParams.addAll(((TYArray) args).getInternalList());
            }
            
            TYObject result = proc.call(newRuntime, null, null, TYObject.NONE, procParams.toArray(new TYObject[procParams.size()]));
            
            newRuntime.disposeVariables(obj.getProcedureRuntime());
            
            return result;
        });
    }
}
