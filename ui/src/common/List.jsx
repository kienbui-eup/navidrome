import React from 'react'
import {
  ListBase,
  ListView,
  ListToolbar,
  ListActions,
  useListContext,
} from 'react-admin'
import { makeStyles } from '@material-ui/core/styles'
import config from '../config'
import { Pagination } from './Pagination'
import { Title } from './index'
import { PageHeaderContent } from '../layout/PageHeader'

const useStyles = makeStyles((theme) => ({
  bulkActionsDisplayed: { '&&': { marginTop: 0 } },
  toolbar: {
    '&&': {
      minHeight: 44,
      padding: 0,
      background: 'transparent',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1),
    },
    '& form': {
      flex: '1 1 180px',
      minWidth: 0,
      minHeight: 0,
      marginTop: 0,
      alignItems: 'center',
    },
    '& .filter-field': { minWidth: 0 },
    '& .filter-field > .MuiFormControl-root': {
      marginTop: 0,
      marginBottom: 0,
      maxWidth: '100%',
    },
    '& .filter-field .MuiInputBase-root': { minHeight: 44 },
    '& .filter-field > div:last-child': { width: theme.spacing(1) },
    '& > span:empty': { display: 'none' },
    [theme.breakpoints.down('xs')]: {
      '&&': { display: 'contents' },
      '& form': { order: 1, flex: '1 1 calc(100% - 48px)' },
      '& .filter-field:first-child': { flex: '1 1 180px' },
      '& .filter-field:first-child > .MuiFormControl-root': { width: '100%' },
    },
  },
  actions: {
    '&&': {
      minHeight: 44,
      padding: 0,
      background: 'transparent',
      alignItems: 'center',
    },
    '& button': { minWidth: 44, minHeight: 44 },
    [theme.breakpoints.down('xs')]: { order: 2, flex: '1 1 auto' },
  },
}))

const HeaderListView = ({ filters, actions = <ListActions />, ...props }) => {
  const controller = useListContext()
  const classes = useStyles()
  return (
    <>
      <PageHeaderContent replaceTitle={!!filters}>
        <ListToolbar
          {...controller}
          filters={filters}
          actions={actions}
          classes={{ toolbar: classes.toolbar, actions: classes.actions }}
        />
      </PageHeaderContent>
      <ListView
        {...props}
        {...controller}
        classes={{
          ...props.classes,
          bulkActionsDisplayed: classes.bulkActionsDisplayed,
        }}
        actions={false}
        filters={undefined}
      />
    </>
  )
}

export const List = (props) => {
  const { resource } = props
  const listProps = {
    title: (
      <Title
        subTitle={`resources.${resource}.name`}
        args={{ smart_count: 2 }}
      />
    ),
    debounce: config.uiSearchDebounceMs,
    filter: {},
    perPage: 15,
    pagination: <Pagination />,
    ...props,
  }
  return (
    <ListBase {...listProps}>
      <HeaderListView {...listProps} />
    </ListBase>
  )
}
