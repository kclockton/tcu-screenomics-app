package edu.stanford.communication.screenomics;

import java.util.List;

public interface FirebaseSettingsObserver{

    void onSettingsChanged(List <String> changedSettings);
}
