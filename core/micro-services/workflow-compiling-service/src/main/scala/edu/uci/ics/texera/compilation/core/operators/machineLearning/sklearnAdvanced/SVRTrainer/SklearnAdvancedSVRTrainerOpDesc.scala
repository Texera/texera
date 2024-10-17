package edu.uci.ics.texera.compilation.core.operators.machineLearning.sklearnAdvanced.SVRTrainer

import edu.uci.ics.texera.compilation.core.operators.machineLearning.sklearnAdvanced.base.SklearnMLOperatorDescriptor
import edu.uci.ics.texera.workflow.operators.machineLearning.sklearnAdvanced.SVRTrainer.SklearnAdvancedSVRParameters
import edu.uci.ics.texera.workflow.operators.machineLearning.sklearnAdvanced.base.SklearnMLOperatorDescriptor

class SklearnAdvancedSVRTrainerOpDesc
    extends SklearnMLOperatorDescriptor[SklearnAdvancedSVRParameters] {
  override def getImportStatements: String = {
    "from sklearn.svm import SVR"
  }

  override def getOperatorInfo: String = {
    "SVM Regressor"
  }
}
