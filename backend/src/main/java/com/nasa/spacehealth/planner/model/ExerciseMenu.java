package com.nasa.spacehealth.planner.model;

import java.util.List;

// contract/exercise_menu.json: the only countermeasures a plan may contain.
public record ExerciseMenu(int minMinutes, int maxMinutesPerSession, List<Item> items) {

    public record Item(
            String exerciseId,
            String label,
            String kind,
            double met,
            String targetSystem,
            Double gravityMaxG,
            Double gravityMinG,
            boolean requiresFlightSurgeonDirection,
            String nasaBasis) {

        public boolean allowedAt(double gravityG) {
            return (gravityMaxG == null || gravityG <= gravityMaxG)
                    && (gravityMinG == null || gravityG >= gravityMinG);
        }

        public boolean isExercise() {
            return "EXERCISE".equals(kind);
        }
    }

    public Item find(String exerciseId) {
        return items.stream().filter(i -> i.exerciseId().equals(exerciseId)).findFirst().orElse(null);
    }

    public List<Item> allowedAt(double gravityG) {
        return items.stream().filter(i -> i.allowedAt(gravityG)).toList();
    }
}
