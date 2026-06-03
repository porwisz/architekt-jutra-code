package pl.devstyle.aj.archetype.pricing;

public enum Applicability {
    ALWAYS {
        @Override
        public boolean isActive(ParameterValue context) {
            return true;
        }
    },
    REFRIGERATED_ONLY {
        @Override
        public boolean isActive(ParameterValue context) {
            return context.requiresRefrigeration();
        }
    };

    public abstract boolean isActive(ParameterValue context);
}
