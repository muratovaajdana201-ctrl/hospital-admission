package hospital.facade;


public record ChainRecord(String chainId, ChainState state, String patientName,
                          String failureReason, String updatedAt) {
}