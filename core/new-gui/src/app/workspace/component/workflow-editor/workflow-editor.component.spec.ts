import { WorkflowActionService } from './../../service/workflow-graph/model/workflow-action.service';
import { UndoRedoService } from './../../service/undo-redo/undo-redo.service';
import { JointGraphWrapper } from './../../service/workflow-graph/model/joint-graph-wrapper';
import { DragDropService } from './../../service/drag-drop/drag-drop.service';
import { WorkflowUtilService } from './../../service/workflow-graph/util/workflow-util.service';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { ValidationWorkflowService } from './../../service/validation/validation-workflow.service';

import { WorkflowEditorComponent } from './workflow-editor.component';

import { OperatorMetadataService } from '../../service/operator-metadata/operator-metadata.service';
import { StubOperatorMetadataService } from '../../service/operator-metadata/stub-operator-metadata.service';
import { JointUIService } from '../../service/joint-ui/joint-ui.service';
import { WorkflowGraph, WorkflowGraphReadonly } from '../../service/workflow-graph/model/workflow-graph';

import * as joint from 'jointjs';

import { ResultPanelToggleService } from '../../service/result-panel-toggle/result-panel-toggle.service';
import { marbles } from 'rxjs-marbles';

import {
  mockScanPredicate, mockPoint, mockScanResultLink, mockResultPredicate
} from '../../service/workflow-graph/model/mock-workflow-data';

class StubWorkflowActionService {

  private jointGraph = new joint.dia.Graph();
  private jointGraphWrapper = new JointGraphWrapper(this.jointGraph);
  private readonly texeraGraph = new WorkflowGraph();

  public attachJointPaper(paperOptions: joint.dia.Paper.Options): joint.dia.Paper.Options {
    paperOptions.model = this.jointGraph;
    return paperOptions;
  }

  public getJointGraphWrapper(): JointGraphWrapper {
    return this.jointGraphWrapper;
  }

  public getTexeraGraph(): WorkflowGraphReadonly {
    return this.texeraGraph;
  }
}

describe('WorkflowEditorComponent', () => {

  /**
   * This sub test suite test if the JointJS paper is integrated with our Angular component well.
   * It uses a fake stub Workflow model that only provides the binding of JointJS graph.
   * It tests if manipulating the JointJS graph is correctly shown in the UI.
   */
  describe('JointJS Paper', () => {
    let component: WorkflowEditorComponent;
    let fixture: ComponentFixture<WorkflowEditorComponent>;
    let jointGraph: joint.dia.Graph;

    beforeEach(async(() => {
      TestBed.configureTestingModule({
        declarations: [WorkflowEditorComponent],
        providers: [
          JointUIService,
          WorkflowUtilService,
          UndoRedoService,
          DragDropService,
          ResultPanelToggleService,
          ValidationWorkflowService,
          { provide: WorkflowActionService, useClass: StubWorkflowActionService },
          { provide: OperatorMetadataService, useClass: StubOperatorMetadataService }
        ]
      })
        .compileComponents();
    }));

    beforeEach(() => {
      fixture = TestBed.createComponent(WorkflowEditorComponent);
      component = fixture.componentInstance;
      // detect changes first to run ngAfterViewInit and bind Model
      fixture.detectChanges();
      jointGraph = component.getJointPaper().model;
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
        position: { x: 100, y: 400 }
      });
      element1.set('id', operator1);

      const element2 = new joint.shapes.basic.Rect({
        size: { width: 100, height: 50 },
        position: { x: 100, y: 400 }
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

  /**
   * This sub test suites test the Integration of WorkflowEditorComponent with external modules,
   *  such as drag and drop module, and highlight operator module.
   */
  describe('External Module Integration', () => {

    let component: WorkflowEditorComponent;
    let fixture: ComponentFixture<WorkflowEditorComponent>;
    let workflowActionService: WorkflowActionService;
    let validationWorkflowService: ValidationWorkflowService;
    let dragDropService: DragDropService;

    beforeEach(async(() => {
      TestBed.configureTestingModule({
        declarations: [WorkflowEditorComponent],
        providers: [
          JointUIService,
          WorkflowUtilService,
          WorkflowActionService,
          UndoRedoService,
          ResultPanelToggleService,
          ValidationWorkflowService,
          DragDropService,
          { provide: OperatorMetadataService, useClass: StubOperatorMetadataService }
        ]
      })
        .compileComponents();
    }));

    beforeEach(() => {
      fixture = TestBed.createComponent(WorkflowEditorComponent);
      component = fixture.componentInstance;
      workflowActionService = TestBed.get(WorkflowActionService);
      validationWorkflowService = TestBed.get(ValidationWorkflowService);
      dragDropService = TestBed.get(DragDropService);
      // detect changes to run ngAfterViewInit and bind Model
      fixture.detectChanges();
    });

    it('should register itself as a droppable element', () => {
      const jqueryElement = jQuery(`#${component.WORKFLOW_EDITOR_JOINTJS_ID}`);
      expect(jqueryElement.data('uiDroppable')).toBeTruthy();
    });

    it('should try to highlight the operator when user mouse clicks on an operator', () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      // install a spy on the highlight operator function and pass the call through
      const highlightOperatorFunctionSpy = spyOn(jointGraphWrapper, 'highlightOperator').and.callThrough();

      workflowActionService.addOperator(mockScanPredicate, mockPoint);

      // find the joint Cell View object of the operator element
      const jointCellView = component.getJointPaper().findViewByModel(mockScanPredicate.operatorID);

      // tirgger a click on the cell view using its jQuery element
      jointCellView.$el.trigger('mousedown');

      fixture.detectChanges();

      // assert the function is called once
     // expect(highlightOperatorFunctionSpy.calls.count()).toEqual(1);
      // assert the highlighted operator is correct
      expect(jointGraphWrapper.getCurrentHighlightedOpeartorID()).toEqual(mockScanPredicate.operatorID);
    });

    it('should react to operator highlight event and change the appearance of the operator to be highlighted', () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      workflowActionService.addOperator(mockScanPredicate, mockPoint);

      // highlight the operator
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // find the joint Cell View object of the operator element
      const jointCellView = component.getJointPaper().findViewByModel(mockScanPredicate.operatorID);

      // find the cell's child element with the joint highlighter class name `joint-highlight-stroke`
      const jointHighlighterElements = jointCellView.$el.children('.joint-highlight-stroke');

      // the element should have the highlighter element in it
      expect(jointHighlighterElements.length).toEqual(1);
    });

    it('should react to operator unhighlight event and change the appearance of the operator to be unhighlighted', () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      workflowActionService.addOperator(mockScanPredicate, mockPoint);

      // highlight the oprator first
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // find the joint Cell View object of the operator element
      const jointCellView = component.getJointPaper().findViewByModel(mockScanPredicate.operatorID);

      // find the cell's child element with the joint highlighter class name `joint-highlight-stroke`
      const jointHighlighterElements = jointCellView.$el.children('.joint-highlight-stroke');

      // the element should have the highlighter element in it right now
      expect(jointHighlighterElements.length).toEqual(1);

      // then unhighlight the operator
      jointGraphWrapper.unhighlightCurrent();

      // the highlighter element should not exist
      const jointHighlighterElementAfterUnhighlight = jointCellView.$el.children('.joint-highlight-stroke');
      expect(jointHighlighterElementAfterUnhighlight.length).toEqual(0);
    });

    it('should react to operator validation and change the color of operator box if the operator is valid ',
         () => {
    const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
    workflowActionService.addOperator(mockScanPredicate, mockPoint);
    workflowActionService.addOperator(mockResultPredicate, mockPoint);
    workflowActionService.addLink(mockScanResultLink);
    const newProperty = { 'tableName': 'test-table' };
    workflowActionService.setOperatorProperty(mockScanPredicate.operatorID, newProperty);
    const operator1 = component.getJointPaper().getModelById(mockScanPredicate.operatorID);
    const operator2 = component.getJointPaper().getModelById(mockResultPredicate.operatorID);
    expect(operator1.attr('rect/stroke')).toEqual('#CFCFCF');
    expect(operator2.attr('rect/stroke')).toEqual('#CFCFCF');

    it('should react to jointJS paper zoom event', marbles((m) => {
      const mockScaleRatio = 0.5;
      m.hot('-e-').do(() => workflowActionService.getJointGraphWrapper().setZoomProperty(mockScaleRatio)).subscribe(
        () => {
          const currentScale = component.getJointPaper().scale();
          expect(currentScale.sx).toEqual(mockScaleRatio);
          expect(currentScale.sy).toEqual(mockScaleRatio);
        }
      );
    }));

    it('should react to jointJS paper restore default offset event', marbles((m) => {
      const mockTranslation = 20;
      const originalOffset = component.getJointPaper().translate();
      component.getJointPaper().translate(mockTranslation, mockTranslation);
      expect(component.getJointPaper().translate().tx).not.toEqual(originalOffset.tx);
      expect(component.getJointPaper().translate().ty).not.toEqual(originalOffset.ty);
      m.hot('-e-').do(() => workflowActionService.getJointGraphWrapper().restoreDefaultZoomAndOffset()).subscribe(
        () => {
          expect(component.getJointPaper().translate().tx).toEqual(originalOffset.tx);
          expect(component.getJointPaper().translate().ty).toEqual(originalOffset.ty);
        }
      );
    }));

  });

    it('should delete the highlighted operator when user presses the backspace key', () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      const texeraGraph = workflowActionService.getTexeraGraph();
      const deleteOperatorFunctionSpy = spyOn(workflowActionService, 'deleteOperator').and.callThrough();

      workflowActionService.addOperator(mockScanPredicate, mockPoint);
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // dispatch a keydown event on the backspace key
      const event = new KeyboardEvent('keydown', {key: 'Backspace'});
      document.dispatchEvent(event);

      fixture.detectChanges();

      // assert the function is called once
      expect(deleteOperatorFunctionSpy.calls.count()).toEqual(1);
      // assert the highlighted operator is deleted
      expect(() => {
        texeraGraph.getOperator(mockScanPredicate.operatorID);
      }).toThrowError(new RegExp(`does not exist`));
    });

    it('should delete the highlighted operator when user presses the delete key', () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      const texeraGraph = workflowActionService.getTexeraGraph();
      const deleteOperatorFunctionSpy = spyOn(workflowActionService, 'deleteOperator').and.callThrough();

      workflowActionService.addOperator(mockScanPredicate, mockPoint);
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // dispatch a keydown event on the backspace key
      const event = new KeyboardEvent('keydown', {key: 'Delete'});
      document.dispatchEvent(event);

      fixture.detectChanges();

      // assert the function is called once
      expect(deleteOperatorFunctionSpy.calls.count()).toEqual(1);
      // assert the highlighted operator is deleted
      expect(() => {
        texeraGraph.getOperator(mockScanPredicate.operatorID);
      }).toThrowError(new RegExp(`does not exist`));
    });

    it(`should create and highlight a new operator with the same metadata when user
        copies and pastes the highlighted operator`, () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      const texeraGraph = workflowActionService.getTexeraGraph();

      workflowActionService.addOperator(mockScanPredicate, mockPoint);
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // dispatch clipboard events for copy and paste
      const copyEvent = new ClipboardEvent('copy');
      document.dispatchEvent(copyEvent);
      const pasteEvent = new ClipboardEvent('paste');
      document.dispatchEvent(pasteEvent);

      // the pasted operator should be highlighted
      const pastedOperatorID = jointGraphWrapper.getCurrentHighlightedOpeartorID();
      expect(pastedOperatorID).toBeDefined();

      // get the pasted operator
      let pastedOperator = null;
      if (pastedOperatorID) {
        pastedOperator = texeraGraph.getOperator(pastedOperatorID);
      }
      expect(pastedOperator).toBeDefined();

      // two operators should have same metadata
      expect(pastedOperatorID).not.toEqual(mockScanPredicate.operatorID);
      if (pastedOperator) {
        expect(pastedOperator.operatorType).toEqual(mockScanPredicate.operatorType);
        expect(pastedOperator.operatorProperties).toEqual(mockScanPredicate.operatorProperties);
        expect(pastedOperator.inputPorts).toEqual(mockScanPredicate.inputPorts);
        expect(pastedOperator.outputPorts).toEqual(mockScanPredicate.outputPorts);
        expect(pastedOperator.showAdvanced).toEqual(mockScanPredicate.showAdvanced);
      }
    });

    it(`should delete the highlighted operator, create and highlight a new operator with the same metadata
        when user cuts and pastes the highlighted operator`, () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();
      const texeraGraph = workflowActionService.getTexeraGraph();

      workflowActionService.addOperator(mockScanPredicate, mockPoint);
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // dispatch clipboard events for cut and paste
      const cutEvent = new ClipboardEvent('cut');
      document.dispatchEvent(cutEvent);
      const pasteEvent = new ClipboardEvent('paste');
      document.dispatchEvent(pasteEvent);

      // the copied operator should be deleted
      expect(() => {
        texeraGraph.getOperator(mockScanPredicate.operatorID);
      }).toThrowError(new RegExp(`does not exist`));

      // the pasted operator should be highlighted
      const pastedOperatorID = jointGraphWrapper.getCurrentHighlightedOpeartorID();
      expect(pastedOperatorID).toBeDefined();

      // get the pasted operator
      let pastedOperator = null;
      if (pastedOperatorID) {
        pastedOperator = texeraGraph.getOperator(pastedOperatorID);
      }
      expect(pastedOperator).toBeDefined();

      // two operators should have same metadata
      expect(pastedOperatorID).not.toEqual(mockScanPredicate.operatorID);
      if (pastedOperator) {
        expect(pastedOperator.operatorType).toEqual(mockScanPredicate.operatorType);
        expect(pastedOperator.operatorProperties).toEqual(mockScanPredicate.operatorProperties);
        expect(pastedOperator.inputPorts).toEqual(mockScanPredicate.inputPorts);
        expect(pastedOperator.outputPorts).toEqual(mockScanPredicate.outputPorts);
        expect(pastedOperator.showAdvanced).toEqual(mockScanPredicate.showAdvanced);
      }
    });

    it('should place the pasted operator in a non-overlapping position', () => {
      const jointGraphWrapper = workflowActionService.getJointGraphWrapper();

      workflowActionService.addOperator(mockScanPredicate, mockPoint);
      jointGraphWrapper.highlightOperator(mockScanPredicate.operatorID);

      // dispatch clipboard events for copy and paste
      const cutEvent = new ClipboardEvent('copy');
      document.dispatchEvent(cutEvent);
      const pasteEvent = new ClipboardEvent('paste');
      document.dispatchEvent(pasteEvent);

      // get the pasted operator
      const pastedOperatorID = jointGraphWrapper.getCurrentHighlightedOpeartorID();
      if (pastedOperatorID) {
        const pastedOperatorPosition = jointGraphWrapper.getOperatorPosition(pastedOperatorID);
        expect(pastedOperatorPosition).not.toEqual(mockPoint);
      }
    });
  });


});
