package hospital.exceptions;


public class ChainNotFoundException extends AdmissionException {
    public ChainNotFoundException(String chainId) {
        super("Unknown chain " + chainId);
    }
}