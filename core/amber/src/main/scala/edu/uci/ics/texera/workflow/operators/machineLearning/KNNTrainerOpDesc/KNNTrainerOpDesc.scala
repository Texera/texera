package edu.uci.ics.texera.workflow.operators.machineLearning.KNNTrainerOpDesc

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaString, JsonSchemaTitle}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.metadata.annotations.{AutofillAttributeName, AutofillAttributeNameList, AutofillAttributeNameOnPort1, HideAnnotation}
import edu.uci.ics.texera.workflow.common.operators.MLOperatorDescriptor

class KNNTrainerOpDesc extends MLOperatorDescriptor{
  @JsonProperty(defaultValue = "false", required = false)
  @JsonSchemaTitle("Using Hyper Parameter Training")
  @JsonSchemaInject(json = """{"toggleHidden" : ["loopK"]}""")
  @JsonPropertyDescription("Tune the parameter K")
  override var parameterTuningFlag: Boolean = false

  @JsonProperty(required = true)
  @JsonSchemaTitle("Ground Truth Attribute Column")
  @JsonPropertyDescription("Ground truth attribute column")
  @AutofillAttributeName
  override var groundTruthAttribute: String = ""

  @JsonProperty(value = "Selected Features", required = true)
  @JsonSchemaTitle("Selected Features")
  @JsonPropertyDescription("Features used to train the model")
  @AutofillAttributeNameList
  override var selectedFeatures: List[String] = _

  @JsonProperty(required = true, defaultValue = "3")
  @JsonSchemaTitle("Custom K")
  @JsonPropertyDescription("Specify the number of nearest neighbours")
  @JsonSchemaInject(
    strings = Array(
      new JsonSchemaString(path = HideAnnotation.hideTarget, value = "parameterTuningFlag"),
      new JsonSchemaString(path = HideAnnotation.hideType, value = HideAnnotation.Type.equals),
      new JsonSchemaString(path = HideAnnotation.hideExpectedValue, value = "true")
    )
  )
  var k: Int = Int.box(1)

  @JsonProperty(required = false, value = "loopK")
  @JsonSchemaTitle("Optimise k from loop")
  @JsonPropertyDescription("Specify which attribute indicates the value of K")
  @JsonSchemaInject(
    strings = Array(
      new JsonSchemaString(path = HideAnnotation.hideTarget, value = "parameterTuningFlag"),
      new JsonSchemaString(path = HideAnnotation.hideType, value = HideAnnotation.Type.equals),
      new JsonSchemaString(path = HideAnnotation.hideExpectedValue, value = "false")
    )
  )
  @AutofillAttributeNameOnPort1
  var loopK: String = ""

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "KNN Trainer",
      "Train a KNN classifier",
      OperatorGroupConstants.MODEL_TRAINING_GROUP,
      inputPorts = List(
        InputPort(
          PortIdentity(0),
          displayName = "dataset",
          allowMultiLinks = true
        ),
        InputPort(
          PortIdentity(1),
          displayName = "parameter",
          allowMultiLinks = true,
          dependencies = List(PortIdentity(0))
        )
      ),
      outputPorts = List(OutputPort())
    )

  override def importPackage(): String = {
    s"""
       |from sklearn.neighbors import KNeighborsClassifier
       |""".stripMargin
  }

  override def trainingModelCustom(): String = {
    s"""
       |        k = $k
       |        knn = KNeighborsClassifier(n_neighbors=k)
       |        knn.fit(X_train, y_train)
       |        para_str = "K = '{}'".format(k)
       |        model_str = pickle.dumps(knn)
       |""".stripMargin
  }

  override def trainingModelOptimization(): String = {
    s"""
       |        param = param.head(1)
       |        k = param["$loopK"].values
       |        for i in k:
       |          k = int(i)
       |          knn = KNeighborsClassifier(n_neighbors=k)
       |          knn.fit(X_train, y_train)
       |          para_str = "K = '{}'".format(k)
       |          model_str = pickle.dumps(knn)
       |""".stripMargin
  }
}