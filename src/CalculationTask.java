import java.util.Arrays;

public class CalculationTask {
    public int id;
    public CalculationTaskStatus status;
    public int[] payload;
    public int result;

    public CalculationTask(int id) {
        this.id = id;
        this.status = CalculationTaskStatus.IDLE;
    }

    @Override
    public String toString() {
        return "{ id:"+this.id+", status:"+this.status+", payload:"+ Arrays.toString(this.payload) +", result:"+this.result+"}";
    }
}
