package net.monacraft.bowling.physics;

public record SimulationConfig(
        int fixedHz,
        int maximumCatchUpSteps,
        double ballRadius,
        double ballMass,
        double ballFriction,
        double gutterFriction,
        double spinCurveCoefficient,
        double spinDecay,
        double ballRestitution,
        double maximumBallSpeed,
        double sideBumperRestitution,
        double sideBumperTangentialRetention,
        double pinRadius,
        double pinFallenSweepRadius,
        double pinMass,
        double pinRestitution,
        double pinLinearFriction,
        double pinAngularDamping,
        double pinTippingThreshold,
        double pinDownThreshold,
        double pinSleepSpeed,
        int pinSleepSteps,
        int maximumSubSteps,
        boolean continuousCollisionDetection,
        double broadphaseCellSize
) {
    public SimulationConfig {
        range(fixedHz, 60, 240, "fixedHz");
        range(maximumCatchUpSteps, 1, 16, "maximumCatchUpSteps");
        positive(ballRadius, "ballRadius");
        positive(ballMass, "ballMass");
        nonNegative(ballFriction, "ballFriction");
        nonNegative(gutterFriction, "gutterFriction");
        nonNegative(spinCurveCoefficient, "spinCurveCoefficient");
        nonNegative(spinDecay, "spinDecay");
        unit(ballRestitution, "ballRestitution");
        positive(maximumBallSpeed, "maximumBallSpeed");
        unit(sideBumperRestitution, "sideBumperRestitution");
        unit(sideBumperTangentialRetention, "sideBumperTangentialRetention");
        positive(pinRadius, "pinRadius");
        positive(pinFallenSweepRadius, "pinFallenSweepRadius");
        positive(pinMass, "pinMass");
        unit(pinRestitution, "pinRestitution");
        nonNegative(pinLinearFriction, "pinLinearFriction");
        nonNegative(pinAngularDamping, "pinAngularDamping");
        positive(pinTippingThreshold, "pinTippingThreshold");
        if (pinDownThreshold <= pinTippingThreshold || pinDownThreshold >= Math.PI / 2.0) {
            throw new IllegalArgumentException("pinDownThreshold must be above tipping threshold and below PI/2");
        }
        nonNegative(pinSleepSpeed, "pinSleepSpeed");
        range(pinSleepSteps, 1, 10_000, "pinSleepSteps");
        range(maximumSubSteps, 1, 32, "maximumSubSteps");
        positive(broadphaseCellSize, "broadphaseCellSize");
    }

    public double fixedDeltaSeconds() {
        return 1.0 / fixedHz;
    }

    public static SimulationConfig hubDefaults() {
        return new SimulationConfig(
                120, 4,
                0.1085, 6.8, 0.018, 0.055,
                0.012, 0.18, 0.30, 12.0, 0.72, 0.96,
                0.13, 0.24, 2.7, 0.34,
                1.35, 1.4,
                Math.toRadians(8.0), Math.toRadians(34.0),
                0.04, 24, 12, true, 0.5
        );
    }

    /**
     * The 100-pin rack is much denser than a ten-pin rack. Reusing the large
     * fallen-pin sweep radius lets one tilted pin overlap the next row and can
     * create a self-sustaining chain after the ball has already stopped. Keep
     * the ball response unchanged, but dissipate pin-to-pin energy sooner.
     */
    public SimulationConfig forHundredPin() {
        return new SimulationConfig(
                fixedHz, maximumCatchUpSteps,
                ballRadius, ballMass, ballFriction, gutterFriction,
                spinCurveCoefficient, spinDecay, ballRestitution, maximumBallSpeed,
                sideBumperRestitution, sideBumperTangentialRetention,
                pinRadius, Math.min(pinFallenSweepRadius, 0.13),
                Math.max(pinMass, 3.2), Math.min(pinRestitution, 0.21),
                Math.max(pinLinearFriction, 1.9), Math.max(pinAngularDamping, 2.4),
                Math.max(pinTippingThreshold, Math.toRadians(11.0)),
                Math.max(pinDownThreshold, Math.toRadians(44.0)),
                Math.max(pinSleepSpeed, 0.05), pinSleepSteps,
                maximumSubSteps, continuousCollisionDetection, broadphaseCellSize
        );
    }

    private static void range(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void unit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
