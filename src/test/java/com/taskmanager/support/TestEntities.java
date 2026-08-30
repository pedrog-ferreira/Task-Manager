package com.taskmanager.support;

import java.lang.reflect.Constructor;

/**
 * Factory for tests to build entities.
 * <p>
 * The entities have {@code @NoArgsConstructor(access = PROTECTED)} — a
 * constructor required by JPA but not meant for direct use by the application.
 * Until there is a {@code @Builder} or a domain factory (coming with the
 * service layer), tests instantiate them via reflection from here, in one place.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not instantiate " + type.getSimpleName(), e);
        }
    }
}
