package com.longhorn.smartlib.task;

import java.util.List;

public interface IEsptouchTask {


    void setEsptouchListener(IEsptouchListener esptouchListener);

    void interrupt();

    IEsptouchResult executeForResult() throws RuntimeException;

    List<IEsptouchResult> executeForResults(int expectTaskResultCount) throws RuntimeException;

    boolean isCancelled();
}
