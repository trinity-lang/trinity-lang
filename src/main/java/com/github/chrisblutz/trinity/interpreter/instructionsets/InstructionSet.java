package com.github.chrisblutz.trinity.interpreter.instructionsets;

import com.github.chrisblutz.trinity.interpreter.variables.Variables;
import com.github.chrisblutz.trinity.lang.*;
import com.github.chrisblutz.trinity.lang.errors.Errors;
import com.github.chrisblutz.trinity.lang.scope.TYRuntime;
import com.github.chrisblutz.trinity.lang.types.TYClassObject;
import com.github.chrisblutz.trinity.lang.types.TYModuleObject;
import com.github.chrisblutz.trinity.lang.types.TYStaticClassObject;
import com.github.chrisblutz.trinity.lang.types.TYStaticModuleObject;
import com.github.chrisblutz.trinity.lang.types.bool.TYBoolean;
import com.github.chrisblutz.trinity.lang.types.numeric.TYFloat;
import com.github.chrisblutz.trinity.lang.types.numeric.TYInt;
import com.github.chrisblutz.trinity.lang.types.numeric.TYLong;
import com.github.chrisblutz.trinity.lang.types.strings.TYString;
import com.github.chrisblutz.trinity.natives.NativeStorage;
import com.github.chrisblutz.trinity.natives.TrinityNatives;
import com.github.chrisblutz.trinity.parser.tokens.Token;
import com.github.chrisblutz.trinity.parser.tokens.TokenInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Christopher Lutz
 */
public class InstructionSet extends ObjectEvaluator {
    
    private TokenInfo[] tokens;
    private List<ChainedInstructionSet> children = new ArrayList<>();
    
    public InstructionSet(TokenInfo[] tokens, String fileName, File fullFile, int lineNumber) {
        
        super(fileName, fullFile, lineNumber);
        
        this.tokens = tokens;
    }
    
    public TokenInfo[] getTokens() {
        
        return tokens;
    }
    
    public void addChild(ChainedInstructionSet set) {
        
        children.add(set);
    }
    
    public List<ChainedInstructionSet> getChildren() {
        
        return children;
    }
    
    public TYObject evaluate(TYObject thisObj, TYRuntime runtime) {
        
        updateLocation();
        
        if (tokens.length >= 1) {
            
            if (tokens[0].getToken() == Token.LITERAL_STRING) {
                
                return new TYString(tokens[0].getContents());
                
            } else if (tokens[0].getToken() == Token.NIL) {
                
                return TYObject.NIL;
                
            } else if (tokens[0].getToken() == Token.NUMERIC_STRING) {
                
                String numString = tokens[0].getContents();
                
                if (numString.matches("[0-9]+[lL]")) {
                    
                    return new TYLong(Long.parseLong(numString.substring(0, numString.length() - 1)));
                    
                } else if (numString.matches("[0-9]*\\.[0-9]+")) {
                    
                    return new TYFloat(Double.parseDouble(numString));
                    
                } else if (numString.matches("[0-9]*\\.?[0-9]+[fF]")) {
                    
                    return new TYFloat(Double.parseDouble(numString.substring(0, numString.length() - 1)));
                    
                } else if (numString.matches("[0-9]+")) {
                    
                    try {
                        
                        return new TYInt(Integer.parseInt(numString));
                        
                    } catch (Exception e) {
                        
                        return new TYLong(Long.parseLong(numString));
                    }
                }
                
            } else if (tokens[0].getToken() == Token.TRUE) {
                
                return TYBoolean.TRUE;
                
            } else if (tokens[0].getToken() == Token.FALSE) {
                
                return TYBoolean.FALSE;
                
            } else if (tokens[0].getToken() == Token.SUPER) {
                
                if (runtime.isStaticScope()) {
                    
                    TYClass thisClass = runtime.getTyClass();
                    return NativeStorage.getStaticClassObject(thisClass.getSuperclass());
                    
                } else {
                    
                    TYObject thisPointer = runtime.getVariable("this");
                    thisPointer.incrementStackLevel();
                    return thisPointer;
                }
                
            } else if (tokens[0].getToken() == Token.__FILE__) {
                
                return new TYString(getFullFile().getAbsolutePath());
                
            } else if (tokens[0].getToken() == Token.__LINE__) {
                
                return new TYInt(getLineNumber());
                
            } else if (tokens[0].getToken() == Token.BLOCK_CHECK) {
                
                return TYBoolean.valueFor(runtime.getProcedure() != null);
                
            } else if (tokens[0].getToken() == Token.BREAK) {
                
                runtime.setBroken(true);
                return null;
                
            } else if (tokens[0].getToken() == Token.CLASS) {
                
                if (thisObj instanceof TYStaticClassObject) {
                    
                    return NativeStorage.getClassObject(((TYStaticClassObject) thisObj).getInternalClass());
                    
                } else {
                    
                    Errors.throwError("Trinity.Errors.SyntaxError", runtime, "Cannot retrieve a class here.");
                }
                
            } else if (tokens[0].getToken() == Token.MODULE) {
                
                if (thisObj instanceof TYStaticModuleObject) {
                    
                    return NativeStorage.getModuleObject(((TYStaticModuleObject) thisObj).getInternalModule());
                    
                } else {
                    
                    Errors.throwError("Trinity.Errors.SyntaxError", runtime, "Cannot retrieve a module here.");
                }
                
            } else if (tokens[0].getToken() == Token.INSTANCE_VAR && tokens.length > 1 && tokens[1].getToken() == Token.NON_TOKEN_STRING) {
                
                String varName = tokens[1].getContents();
                
                if (!runtime.isStaticScope()) {
                    
                    if (Variables.getInstanceVariables(runtime.getScope()).containsKey(varName)) {
                        
                        return Variables.getInstanceVariables(runtime.getScope()).get(varName);
                        
                    } else {
                        
                        Errors.throwError("Trinity.Errors.FieldNotFoundError", runtime, "Instance variable '" + varName + "' not found.");
                    }
                    
                } else {
                    
                    Errors.throwError("Trinity.Errors.ScopeError", runtime, "Instance variable '" + varName + "' not accessible from a static context.");
                }
                
            } else if (tokens[0].getToken() == Token.CLASS_VAR && tokens.length > 1 && tokens[1].getToken() == Token.NON_TOKEN_STRING) {
                
                String varName = tokens[1].getContents();
                
                if (runtime.getTyClass().hasVariable(varName)) {
                    
                    return runtime.getTyClass().getVariable(varName);
                    
                } else {
                    
                    Errors.throwError("Trinity.Errors.FieldNotFoundError", runtime, "Class field '" + varName + "' not found.");
                }
                
            } else if (tokens[0].getToken() == Token.GLOBAL_VAR && tokens.length > 1 && tokens[1].getToken() == Token.NON_TOKEN_STRING) {
                
                String varName = tokens[1].getContents();
                
                if (Variables.hasGlobalVariable(varName)) {
                    
                    return Variables.getGlobalVariable(varName);
                    
                } else {
                    
                    Errors.throwError("Trinity.Errors.FieldNotFoundError", runtime, "Global field '" + varName + "' not found.");
                }
                
            } else if (tokens[0].getToken() == Token.NON_TOKEN_STRING) {
                
                String tokenContents = tokens[0].getContents();
                
                if (thisObj == TYObject.NONE) {
                    
                    if (runtime.hasVariable(tokenContents)) {
                        
                        return runtime.getVariable(tokenContents);
                        
                    } else if (runtime.getModule() != null && runtime.getModule().hasClass(tokenContents)) {
                        
                        return NativeStorage.getStaticClassObject(runtime.getModule().getClass(tokenContents));
                        
                    } else if (runtime.hasImportedModuleWithClass(tokenContents)) {
                        
                        return NativeStorage.getStaticClassObject(runtime.getImportedClassWithModule(tokenContents));
                        
                    } else if (ModuleRegistry.moduleExists(tokenContents)) {
                        
                        return NativeStorage.getStaticModuleObject(ModuleRegistry.getModule(tokenContents));
                        
                    } else if (ClassRegistry.classExists(tokenContents)) {
                        
                        return NativeStorage.getStaticClassObject(ClassRegistry.getClass(tokenContents));
                        
                    } else if (ModuleRegistry.getModule("Trinity").hasClass(tokenContents)) {
                        
                        return NativeStorage.getStaticClassObject(ModuleRegistry.getModule("Trinity").getClass(tokenContents));
                        
                    } else {
                        
                        List<TYObject> params = new ArrayList<>();
                        
                        for (ChainedInstructionSet set : getChildren()) {
                            
                            TYObject obj;
                            
                            obj = set.evaluate(TYObject.NONE, runtime);
                            
                            if (obj != TYObject.NONE) {
                                
                                params.add(obj);
                            }
                        }
                        
                        if (runtime.isStaticScope()) {
                            
                            return TrinityNatives.cast(TYClassObject.class, runtime.getScope()).getInternalClass().tyInvoke(tokenContents, runtime, getProcedure(), runtime, TYObject.NONE, params.toArray(new TYObject[params.size()]));
                            
                        } else {
                            
                            return runtime.getScope().tyInvoke(tokenContents, runtime, getProcedure(), runtime, params.toArray(new TYObject[params.size()]));
                        }
                    }
                    
                } else if (thisObj instanceof TYStaticModuleObject) {
                    
                    TYStaticModuleObject moduleObject = (TYStaticModuleObject) thisObj;
                    TYModule tyModule = moduleObject.getInternalModule();
                    
                    if (tyModule.hasModule(tokenContents)) {
                        
                        return NativeStorage.getStaticModuleObject(tyModule.getModule(tokenContents));
                        
                    } else if (tyModule.hasClass(tokenContents)) {
                        
                        return NativeStorage.getStaticClassObject(tyModule.getClass(tokenContents));
                    }
                    
                } else if (thisObj instanceof TYModuleObject) {
                    
                    List<TYObject> params = new ArrayList<>();
                    
                    for (ChainedInstructionSet set : getChildren()) {
                        
                        TYObject obj = set.evaluate(TYObject.NONE, runtime);
                        
                        if (obj != TYObject.NONE) {
                            
                            params.add(obj);
                        }
                    }
                    
                    return thisObj.tyInvoke(tokenContents, runtime, getProcedure(), runtime, params.toArray(new TYObject[params.size()]));
                    
                } else if (thisObj instanceof TYStaticClassObject) {
                    
                    TYStaticClassObject classObject = (TYStaticClassObject) thisObj;
                    TYClass tyClass = classObject.getInternalClass();
                    
                    if (tyClass.hasClass(tokenContents)) {
                        
                        return NativeStorage.getStaticClassObject(tyClass.getClass(tokenContents));
                        
                    } else {
                        
                        List<TYObject> params = new ArrayList<>();
                        
                        for (ChainedInstructionSet set : getChildren()) {
                            
                            TYObject obj = set.evaluate(TYObject.NONE, runtime);
                            
                            if (obj != TYObject.NONE) {
                                
                                params.add(obj);
                            }
                        }
                        
                        return classObject.getInternalClass().tyInvoke(tokenContents, runtime, getProcedure(), runtime, TYObject.NONE, params.toArray(new TYObject[params.size()]));
                    }
                    
                } else if (thisObj instanceof TYClassObject) {
                    
                    List<TYObject> params = new ArrayList<>();
                    for (ChainedInstructionSet set : getChildren()) {
                        
                        TYObject obj = set.evaluate(TYObject.NONE, runtime);
                        
                        if (obj != TYObject.NONE) {
                            
                            params.add(obj);
                        }
                    }
                    
                    return thisObj.tyInvoke(tokenContents, runtime, getProcedure(), runtime, params.toArray(new TYObject[params.size()]));
                    
                } else {
                    
                    List<TYObject> params = new ArrayList<>();
                    
                    for (ChainedInstructionSet set : getChildren()) {
                        
                        TYObject obj = set.evaluate(TYObject.NONE, runtime);
                        
                        if (obj != TYObject.NONE) {
                            
                            params.add(obj);
                        }
                    }
                    
                    return thisObj.tyInvoke(tokenContents, runtime, getProcedure(), runtime, params.toArray(new TYObject[params.size()]));
                }
            }
        }
        
        return TYObject.NONE;
    }
    
    @Override
    public String toString() {
        
        return toString("");
    }
    
    @Override
    public String toString(String indent) {
        
        StringBuilder str = new StringBuilder(indent + "InstructionSet [");
        
        for (TokenInfo info : getTokens()) {
            
            str.append(info.getContents());
        }
        
        str.append("]");
        
        for (ObjectEvaluator child : getChildren()) {
            
            str.append("\n").append(indent).append(child.toString(indent + "\t"));
        }
        
        return str.toString();
    }
}
