package com.appgiuaky.gpaplanner.types;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Toàn bộ bảng điểm của người dùng. Lưu thành file JSON trong máy; định dạng giữ nguyên như bản 1.0
 * (viết bằng Kotlin) để người đã dùng bản cũ cập nhật lên vẫn còn dữ liệu.
 */
public final class AcademicData {

    public static final int DEFAULT_PROGRAM_CREDITS = 130;

    public final List<Semester> semesters;
    /** Tổng số tín chỉ của chương trình đào tạo, dùng để tính số tín chỉ còn lại. */
    public int totalProgramCredits;

    public AcademicData(List<Semester> semesters, int totalProgramCredits) {
        this.semesters = new ArrayList<>(semesters);
        this.totalProgramCredits = totalProgramCredits;
    }

    public String toJson() throws JSONException {
        JSONArray semesterArray = new JSONArray();
        for (Semester s : semesters) {
            JSONArray courseArray = new JSONArray();
            for (Course c : s.courses) {
                JSONArray componentArray = new JSONArray();
                for (GradeComponent g : c.components) {
                    JSONObject o = new JSONObject().put("name", g.name).put("weight", g.weight);
                    if (g.score != null) o.put("score", g.score.doubleValue());
                    componentArray.put(o);
                }
                courseArray.put(new JSONObject()
                        .put("id", c.id)
                        .put("code", c.code)
                        .put("name", c.name)
                        .put("credits", c.credits)
                        .put("components", componentArray)
                        .put("countInGpa", c.countInGpa));
            }
            semesterArray.put(new JSONObject().put("id", s.id).put("name", s.name).put("courses", courseArray));
        }
        return new JSONObject()
                .put("semesters", semesterArray)
                .put("totalProgramCredits", totalProgramCredits)
                .toString();
    }

    /** Các trường có giá trị mặc định có thể vắng mặt (bản 1.0 không ghi chúng ra file). */
    public static AcademicData fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray semesterArray = root.getJSONArray("semesters");
        List<Semester> semesters = new ArrayList<>();
        for (int i = 0; i < semesterArray.length(); i++) {
            JSONObject s = semesterArray.getJSONObject(i);
            JSONArray courseArray = s.optJSONArray("courses");
            List<Course> courses = new ArrayList<>();
            for (int j = 0; courseArray != null && j < courseArray.length(); j++) {
                JSONObject c = courseArray.getJSONObject(j);
                JSONArray componentArray = c.getJSONArray("components");
                List<GradeComponent> components = new ArrayList<>();
                for (int k = 0; k < componentArray.length(); k++) {
                    JSONObject g = componentArray.getJSONObject(k);
                    Double score = g.isNull("score") ? null : g.getDouble("score");
                    components.add(new GradeComponent(g.getString("name"), g.getDouble("weight"), score));
                }
                courses.add(new Course(
                        c.optString("id", UUID.randomUUID().toString()),
                        c.getString("code"),
                        c.getString("name"),
                        c.getInt("credits"),
                        components,
                        c.optBoolean("countInGpa", true)));
            }
            semesters.add(new Semester(s.optString("id", UUID.randomUUID().toString()), s.getString("name"), courses));
        }
        return new AcademicData(semesters, root.optInt("totalProgramCredits", DEFAULT_PROGRAM_CREDITS));
    }
}
