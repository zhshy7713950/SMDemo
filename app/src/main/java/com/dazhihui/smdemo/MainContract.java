package com.dazhihui.smdemo;

/**
 * Created by Android on 2018/3/16.
 */

public interface MainContract {

    interface View {

        void setLoadingIndicator(boolean active);

        void setPresenter(Presenter presenter);

        void showMessage(String description);

        void showResult(String result);
    }

    interface Presenter{

        void sendAE();

        void sendA0();

        void sendAC();
    }
}
