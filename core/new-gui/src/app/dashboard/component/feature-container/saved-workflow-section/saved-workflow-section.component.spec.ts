import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SavedWorkflowSectionComponent } from './saved-workflow-section.component';
import { WorkflowPersistService } from '../../../../common/service/user/workflow-persist/workflow-persist.service';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule } from '@angular/material/dialog';

import { NgbActiveModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';

import { HttpClientModule } from '@angular/common/http';
import { Workflow } from '../../../../common/type/workflow';

describe('SavedProjectSectionComponent', () => {
  let component: SavedWorkflowSectionComponent;
  let fixture: ComponentFixture<SavedWorkflowSectionComponent>;

  const TestCase: Workflow[] = [
    {
      wfId: 1,
      name: 'project 1',
      content: '{}',
      creationTime: '2017-10-25T12:34:50Z',
      lastModifiedTime: '2018-01-17T06:26:50Z',
    },
    {
      wfId: 2,
      name: 'project 2',
      content: '{}',
      creationTime: '2017-10-30T01:02:50Z',
      lastModifiedTime: '2018-01-14T22:56:50Z',
    },
    {
      wfId: 3,
      name: 'project 3',
      content: '{}',
      creationTime: '2018-01-01T01:01:01Z',
      lastModifiedTime: '2018-01-22T17:26:50Z',
    },
    {
      wfId: 4,
      name: 'project 4',
      content: '{}',
      creationTime: '2017-10-25T12:34:50Z',
      lastModifiedTime: '2018-01-17T06:26:50Z',
    },
    {
      wfId: 5,
      name: 'project 5',
      content: '{}',
      creationTime: '2017-10-30T01:02:50Z',
      lastModifiedTime: '2018-01-14T22:56:50Z',
    }
  ];

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [SavedWorkflowSectionComponent],
      providers: [
        WorkflowPersistService,
        NgbActiveModal
      ],
      imports: [MatDividerModule,
        MatListModule,
        MatCardModule,
        MatDialogModule,
        NgbModule,
        FormsModule,
        HttpClientModule]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SavedWorkflowSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('alphaSortTest increaseOrder', () => {
    component.workflows = [];
    component.workflows = component.workflows.concat(TestCase);
    component.ascSort();
    const SortedCase = component.workflows.map(item => item.name);
    expect(SortedCase)
      .toEqual(['project 1', 'project 2', 'project 3', 'project 4', 'project 5']);
  });

  it('alphaSortTest decreaseOrder', () => {
    component.workflows = [];
    component.workflows = component.workflows.concat(TestCase);
    component.dscSort();
    const SortedCase = component.workflows.map(item => item.name);
    expect(SortedCase)
      .toEqual(['project 5', 'project 4', 'project 3', 'project 2', 'project 1']);
  });

  it('createDateSortTest', () => {
    component.workflows = [];
    component.workflows = component.workflows.concat(TestCase);
    component.dateSort();
    const SortedCase = component.workflows.map(item => item.creationTime);
    expect(SortedCase)
      .toEqual(['2018-01-01T01:01:01Z', '2017-10-30T01:02:50Z', '2017-10-30T01:02:50Z', '2017-10-25T12:34:50Z', '2017-10-25T12:34:50Z']);
  });

  it('lastEditSortTest', () => {
    component.workflows = [];
    component.workflows = component.workflows.concat(TestCase);
    component.lastSort();
    const SortedCase = component.workflows.map(item => item.lastModifiedTime);
    expect(SortedCase)
      .toEqual(['2018-01-22T17:26:50Z', '2018-01-17T06:26:50Z', '2018-01-17T06:26:50Z', '2018-01-14T22:56:50Z', '2018-01-14T22:56:50Z']);
  });

  /*
  * more tests of testing return value from pop-up components(windows)
  * should be removed to here
  */

});
