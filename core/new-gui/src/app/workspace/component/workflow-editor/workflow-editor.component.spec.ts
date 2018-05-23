import { WorkflowUtilService } from './../../service/workflow-graph/util/workflow-util.service';
import { JointModelService } from './../../service/workflow-graph/model/joint-model.service';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkflowEditorComponent } from './workflow-editor.component';

import { OperatorMetadataService } from '../../service/operator-metadata/operator-metadata.service';
import { StubOperatorMetadataService } from '../../service/operator-metadata/stub-operator-metadata.service';
import { JointUIService } from '../../service/joint-ui/joint-ui.service';

import * as joint from 'jointjs';
import { WorkflowActionService } from '../../service/workflow-graph/model/workflow-action.service';

describe('WorkflowEditorComponent', () => {
  let component: WorkflowEditorComponent;
  let fixture: ComponentFixture<WorkflowEditorComponent>;
  let jointUIService: JointUIService;
  let jointGraph: joint.dia.Graph;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [WorkflowEditorComponent],
      providers: [
        JointUIService,
        JointModelService,
        WorkflowActionService,
        WorkflowUtilService,
        { provide: OperatorMetadataService, useClass: StubOperatorMetadataService },
      ]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(WorkflowEditorComponent);
    component = fixture.componentInstance;
    jointUIService = fixture.debugElement.injector.get(JointUIService);
    const jointModelService = fixture.debugElement.injector.get(JointModelService);
    jointGraph = (jointModelService as any).jointGraph;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should create element in the UI after adding operator in the model', () => {
    const operatorID = 'test_one_operator_1';

    const element = new joint.shapes.basic.Rect();
    element.set('id', operatorID);

    jointGraph.addCell(element);

    expect(component.getJointPaper().findViewByModel(element.id)).toBeTruthy();

  });

  it('should create a graph of multiple cells in the UI', () => {
    const operator1 = 'test_multiple_1_op_1';
    const operator2 = 'test_multiple_1_op_2';

    const element1 = new joint.shapes.basic.Rect({
      size: { width: 100, height: 50 },
      position: { x: 100, y: 400}
    });
    element1.set('id', operator1);

    const element2 = new joint.shapes.basic.Rect({
      size: { width: 100, height: 50 },
      position: { x: 100, y: 400}
    });
    element2.set('id', operator2);

    const link1 = new joint.dia.Link({
      source: { id: operator1 },
      target: { id: operator2 }
    });

    jointGraph.addCell(element1);
    jointGraph.addCell(element2);
    jointGraph.addCell(link1);

    // check the model is added correctly
    expect(jointGraph.getElements().find(el => el.id === operator1)).toBeTruthy();
    expect(jointGraph.getElements().find(el => el.id === operator2)).toBeTruthy();
    expect(jointGraph.getLinks().find(link => link.id === link1.id)).toBeTruthy();


    // check the view is updated correctly
    expect(component.getJointPaper().findViewByModel(element1.id)).toBeTruthy();
    expect(component.getJointPaper().findViewByModel(element2.id)).toBeTruthy();
    expect(component.getJointPaper().findViewByModel(link1.id)).toBeTruthy();

  });

});
