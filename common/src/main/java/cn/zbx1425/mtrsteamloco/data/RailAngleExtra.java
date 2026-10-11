package cn.zbx1425.mtrsteamloco.data;

import mtr.data.RailAngle;
import cn.zbx1425.mtrsteamloco.Main;

public interface RailAngleExtra {
    RailAngle _fromDegrees(double degrees);

    RailAngle _fromRadians(double radians);

    void setRadians(double radians);

    static RailAngle fromDegrees(double degrees) {
        RailAngle result = ((RailAngleExtra) (Object) RailAngle.S)._fromDegrees(degrees);
        return result;
    }

    static RailAngle fromRadians(double radians) {
        RailAngle result = ((RailAngleExtra) (Object) RailAngle.S)._fromRadians(radians);
        return result;
    }
}