package com.jupin.common.context;

public class BaseContext {
    private static final ThreadLocal<Long> threadLocalId = new ThreadLocal<>();
    private static final ThreadLocal<Integer> threadLocalRole = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocalId.set(id);
    }

    public static Long getCurrentId() {
        return threadLocalId.get();
    }

    public static void setCurrentRole(Integer role) {
        threadLocalRole.set(role);
    }

    public static Integer getCurrentRole() {
        return threadLocalRole.get();
    }

    public static void remove() {
        threadLocalId.remove();
        threadLocalRole.remove();
    }
}
