package com.appgiuaky.gpaplanner.types;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Semester {

    public final String id;
    public String name;
    public final List<Course> courses;

    public Semester(String id, String name, List<Course> courses) {
        this.id = id;
        this.name = name;
        this.courses = new ArrayList<>(courses);
    }

    public Semester(String name, List<Course> courses) {
        this(UUID.randomUUID().toString(), name, courses);
    }
}
