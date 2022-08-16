package com.demo;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

public class MethodAdapterVisitor extends AdviceAdapter {

    // 是否需要插桩
    private boolean inject = false;

    private String methodName = "";

    /**
     * Constructs a new {@link AdviceAdapter}.
     *
     * @param api           the ASM API version implemented by this visitor. Must be one of {@link
     *                      Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or {@link Opcodes#ASM7}.
     * @param methodVisitor the method visitor to which this adapter delegates calls.
     * @param access        the method's access flags (see {@link Opcodes}).
     * @param name          the method's name.
     * @param descriptor    the method's descriptor (see {@link Type Type}).
     */
    protected MethodAdapterVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
        super(api, methodVisitor, access, name, descriptor);
        methodName = name;
    }


    /**
     * 分析方法上面的注解
     * <p>
     * 判断当前这个方法是不是使用了injecttime，如果使用了，我们就需要对这个方法插桩
     * 没使用，就不管了。
     *
     * @param desc
     * @param visible
     * @return
     */
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        // 如果方法加上了ASMTest.class的注解，才会执行插桩
//        if (Type.getDescriptor(ASMTest.class).equals(desc)) {
//            System.out.println(desc);
//            inject = true;
//        }
        return super.visitAnnotation(desc, visible);
    }

    // 记录开始时间 定义成全局变量以便在后面执行加减法的时候可以方便获取到
    private int start;

    /**
     * 在方法开始时插入代码：
     * long start = System.currentTimeMillis();
     */
    @Override
    protected void onMethodEnter() {
        super.onMethodEnter();
        if (inject) {
            //执行完了怎么办？记录到本地变量中
            invokeStatic(Type.getType("Ljava/lang/System;"),
                    new Method("currentTimeMillis", "()J"));

            start = newLocal(Type.LONG_TYPE); //创建本地 LONG类型变量
            //记录 方法执行结果给创建的本地变量
            storeLocal(start);
        }
    }

    /**
     * 在方法结束时插入代码：
     * long end = System.currentTimeMillis();
     * System.out.println("方法耗时："+(end-start));
     */
    @Override
    protected void onMethodExit(int opcode) {
        super.onMethodExit(opcode);
        if (inject) {
            invokeStatic(Type.getType("Ljava/lang/System;"),
                    new Method("currentTimeMillis", "()J"));
            int end = newLocal(Type.LONG_TYPE);
            storeLocal(end);

            getStatic(Type.getType("Ljava/lang/System;"), "out", Type.getType("Ljava/io" +
                    "/PrintStream;"));

            //分配内存 并dup压入栈顶让下面的INVOKESPECIAL 知道执行谁的构造方法创建StringBuilder
            newInstance(Type.getType("Ljava/lang/StringBuilder;"));
            dup();
            invokeConstructor(Type.getType("Ljava/lang/StringBuilder;"), new Method("<init>", "()V"));


            visitLdcInsn("execute:" + methodName);
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));

            //减法
            loadLocal(end);
            loadLocal(start);
            math(SUB, Type.LONG_TYPE);


            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(J)Ljava/lang/StringBuilder;"));
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("toString", "()Ljava/lang/String;"));
            invokeVirtual(Type.getType("Ljava/io/PrintStream;"), new Method("println", "(Ljava/lang/String;)V"));

        }
    }
}
