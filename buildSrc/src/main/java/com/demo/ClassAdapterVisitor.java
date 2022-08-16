package com.demo;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import groovyjarjarasm.asm.Opcodes;

public class ClassAdapterVisitor extends ClassVisitor {


    public ClassAdapterVisitor(ClassVisitor cv) {
        super(Opcodes.ASM7, cv);
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//        System.out.println("方法:" + name + " 签名:" + descriptor);
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature,
                exceptions);
        return new MethodAdapterVisitor(api,mv, access, name, descriptor);
    }

    //    public ClassAdapterVisitor(int api, ClassVisitor classVisitor) {
//        System.out.println("方法:" + name + " 签名:" + desc);
//        MethodVisitor mv = super.visitMethod(access, name, desc, signature,
//                exceptions);
//        return new MethodAdapterVisitor(api,mv, access, name, desc);
//    }
}
